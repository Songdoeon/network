package com.network.loadgen.runner;

import com.network.common.dto.AuthorizeResponse;
import com.network.loadgen.client.GatewayClient;
import com.network.loadgen.config.LoadGenProperties;
import com.network.loadgen.report.ResultReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

@Component
public class LoadRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadRunner.class);

    private final LoadGenProperties properties;
    private final GatewayClient client;
    private final ResultReporter reporter;

    public LoadRunner(LoadGenProperties properties, GatewayClient client, ResultReporter reporter) {
        this.properties = properties;
        this.client = client;
        this.reporter = reporter;
    }

    @Override
    public void run(String... args) throws Exception {
        ScenarioType scenario = ScenarioType.BURST;
        if (args.length > 0) {
            try {
                scenario = ScenarioType.valueOf(args[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown scenario: {}, defaulting to BURST", args[0]);
            }
        }

        log.info("Starting load test: scenario={}, users={}, duration={}s",
                scenario, properties.getConcurrentUsers(), properties.getDurationSeconds());

        reporter.reset();

        switch (scenario) {
            case BURST -> runBurst();
            case SLOWDOWN -> runSteady();
            case SESSION_DROP -> runSteady();
            case OUT_OF_ORDER -> runSteady();
        }

        reporter.printReport();
    }

    private void runBurst() throws InterruptedException {
        int burstMultiplier = 20;
        int burstUsers = properties.getConcurrentUsers() * burstMultiplier;
        int burstDurationSec = 10;

        log.info("BURST scenario: {} concurrent users for {}s", burstUsers, burstDurationSec);

        CountDownLatch latch = new CountDownLatch(1);

        Flux.interval(Duration.ZERO, Duration.ofMillis(1000 / burstUsers))
                .take(Duration.ofSeconds(burstDurationSec))
                .flatMap(tick -> sendRequest(), burstUsers)
                .doOnComplete(() -> {
                    log.info("Burst completed");
                    latch.countDown();
                })
                .subscribe();

        latch.await();
    }

    private void runSteady() throws InterruptedException {
        int users = properties.getConcurrentUsers();
        int durationSec = properties.getDurationSeconds();

        log.info("Steady scenario: {} concurrent users for {}s", users, durationSec);

        CountDownLatch latch = new CountDownLatch(1);

        long intervalMs = Math.max(1, 1000 / users);

        Flux.interval(Duration.ZERO, Duration.ofMillis(intervalMs))
                .take(Duration.ofSeconds(durationSec))
                .flatMap(tick -> sendRequest(), users)
                .doOnComplete(() -> {
                    log.info("Steady test completed");
                    latch.countDown();
                })
                .subscribe();

        latch.await();
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
