package com.network.loadgen.runner;

import com.network.common.dto.AuthorizeResponse;
import com.network.loadgen.client.CardSimClient;
import com.network.loadgen.client.GatewayClient;
import com.network.loadgen.config.LoadGenProperties;
import com.network.loadgen.report.ResultReporter;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class LoadRunner {

    private final LoadGenProperties properties;
    private final GatewayClient client;
    private final CardSimClient cardSimClient;
    private final ResultReporter reporter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "scenario-runner"));

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Run a scenario asynchronously. Returns a future with the result.
     */
    public CompletableFuture<ScenarioResult> runScenario(ScenarioType scenario) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Starting load test: scenario={}, users={}, duration={}s",
                    scenario, properties.getConcurrentUsers(), properties.getDurationSeconds());

            reporter.reset();
            reporter.startSampling(properties.getGatewayUrl());

            try {
                switch (scenario) {
                    case BURST -> runBurst();
                    case SLOWDOWN -> runSlowdown();
                    case SESSION_DROP -> runSessionDrop();
                    case OUT_OF_ORDER -> runOutOfOrder();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Scenario interrupted: {}", scenario);
            } finally {
                reporter.stopSampling();
            }

            reporter.printReport(scenario.name());
            return reporter.toResult(scenario.name());
        }, executor);
    }

    private void runBurst() throws InterruptedException {
        int burstMultiplier = 20;
        int burstUsers = properties.getConcurrentUsers() * burstMultiplier;
        int burstDurationSec = 10;
        long intervalMs = Math.max(1, 1000 / burstUsers);

        log.info("BURST scenario: {} concurrent users for {}s (interval={}ms)", burstUsers, burstDurationSec, intervalMs);

        // 외부기관도 부하로 인해 느려지는 현실적 조건 시뮬레이션
        updateCardSim(Map.of("baseLatencyMs", 500, "p95LatencyMs", 800));

        CountDownLatch latch = new CountDownLatch(1);

        Flux.interval(Duration.ZERO, Duration.ofMillis(intervalMs))
                .take(Duration.ofSeconds(burstDurationSec))
                .onBackpressureDrop(tick -> log.trace("Dropped tick {}", tick))
                .flatMap(tick -> sendRequest(), burstUsers)
                .doOnComplete(() -> {
                    log.info("Burst completed");
                    latch.countDown();
                })
                .subscribe();

        latch.await();
        resetCardSim();
    }

    private void runSlowdown() throws InterruptedException {
        int durationSec = 30;
        log.info("SLOWDOWN scenario: steady traffic + gradual latency increase for {}s", durationSec);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.schedule(() -> updateCardSim(Map.of("baseLatencyMs", 100)), 5, TimeUnit.SECONDS);
        scheduler.schedule(() -> updateCardSim(Map.of("baseLatencyMs", 200)), 10, TimeUnit.SECONDS);
        scheduler.schedule(() -> updateCardSim(Map.of("baseLatencyMs", 300)), 15, TimeUnit.SECONDS);
        scheduler.schedule(() -> updateCardSim(Map.of("baseLatencyMs", 30)), 20, TimeUnit.SECONDS);

        CountDownLatch latch = new CountDownLatch(1);
        runSteadyTraffic(durationSec, latch);
        latch.await();

        shutdownScheduler(scheduler);
        resetCardSim();
    }

    private void runSessionDrop() throws InterruptedException {
        int durationSec = 35;
        log.info("SESSION_DROP scenario: steady traffic + disconnect injection for {}s", durationSec);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.schedule(() -> updateCardSim(Map.of("disconnectEverySec", 10)), 10, TimeUnit.SECONDS);
        scheduler.schedule(() -> updateCardSim(Map.of("disconnectEverySec", 0)), 25, TimeUnit.SECONDS);

        CountDownLatch latch = new CountDownLatch(1);
        runSteadyTraffic(durationSec, latch);
        latch.await();

        shutdownScheduler(scheduler);
        resetCardSim();
    }

    private void runOutOfOrder() throws InterruptedException {
        int durationSec = 30;
        log.info("OUT_OF_ORDER scenario: steady traffic + out-of-order responses for {}s", durationSec);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.schedule(() -> updateCardSim(Map.of("outOfOrderRate", 0.3)), 5, TimeUnit.SECONDS);
        scheduler.schedule(() -> updateCardSim(Map.of("outOfOrderRate", 0.0)), 25, TimeUnit.SECONDS);

        CountDownLatch latch = new CountDownLatch(1);
        runSteadyTraffic(durationSec, latch);
        latch.await();

        shutdownScheduler(scheduler);
        resetCardSim();
    }

    private void runSteadyTraffic(int durationSec, CountDownLatch latch) {
        int users = properties.getConcurrentUsers();
        long intervalMs = Math.max(1, 1000 / users);

        Flux.interval(Duration.ZERO, Duration.ofMillis(intervalMs))
                .take(Duration.ofSeconds(durationSec))
                .onBackpressureDrop()
                .flatMap(tick -> sendRequest(), users)
                .doOnComplete(() -> {
                    log.info("Steady test completed");
                    latch.countDown();
                })
                .subscribe();
    }

    private void shutdownScheduler(ScheduledExecutorService scheduler) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void updateCardSim(Map<String, Object> config) {
        log.info("Updating card-sim config: {}", config);
        cardSimClient.updateConfig(config)
                .doOnError(e -> log.warn("card-sim config update failed: {}", e.getMessage()))
                .subscribe();
    }

    private void resetCardSim() {
        log.info("Resetting card-sim config to defaults");
        cardSimClient.updateConfig(Map.of(
                "baseLatencyMs", 30,
                "p95LatencyMs", 100,
                "errorRate", 0.01,
                "disconnectEverySec", 0,
                "outOfOrderRate", 0.0
        )).subscribe();
    }

    private Mono<AuthorizeResponse> sendRequest() {
        long start = System.currentTimeMillis();
        return client.authorize()
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(response -> {
                    long latency = response.latencyMs() > 0 ? response.latencyMs() : (System.currentTimeMillis() - start);
                    reporter.record(response.status(), latency);
                });
    }
}
