package com.network.loadgen.demo;

import com.network.loadgen.report.ResultReporter;
import com.network.loadgen.runner.LoadRunner;
import com.network.loadgen.runner.ScenarioResult;
import com.network.loadgen.runner.ScenarioType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class DemoController {

    private final LoadRunner loadRunner;
    private final ResultReporter reporter;
    private final MetricsProxy metricsProxy;

    private enum State { IDLE, RUNNING, COMPLETED }

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final AtomicReference<String> currentScenario = new AtomicReference<>("");
    private final AtomicLong startTimeMs = new AtomicLong(0);
    private final AtomicReference<ScenarioResult> lastResult = new AtomicReference<>();

    @PostMapping("/scenarios/{type}")
    public ResponseEntity<Map<String, Object>> startScenario(@PathVariable String type) {
        ScenarioType scenarioType;
        try {
            scenarioType = ScenarioType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown scenario: " + type));
        }

        if (!state.compareAndSet(State.IDLE, State.RUNNING) &&
            !state.compareAndSet(State.COMPLETED, State.RUNNING)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Scenario already running"));
        }

        currentScenario.set(scenarioType.name());
        startTimeMs.set(System.currentTimeMillis());
        lastResult.set(null);

        CompletableFuture<ScenarioResult> future = loadRunner.runScenario(scenarioType);
        future.whenComplete((result, error) -> {
            if (error != null) {
                log.error("Scenario failed: {}", error.getMessage());
            }
            lastResult.set(result);
            state.set(State.COMPLETED);
            log.info("Scenario {} completed", scenarioType);
        });

        return ResponseEntity.accepted().body(Map.of(
                "scenario", scenarioType.name(),
                "startedAt", startTimeMs.get()
        ));
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        State current = state.get();
        long elapsed = current == State.RUNNING
                ? (System.currentTimeMillis() - startTimeMs.get()) / 1000
                : 0;

        ScenarioResult snapshot = current == State.RUNNING
                ? reporter.getSnapshot()
                : lastResult.get();

        return Map.of(
                "state", current.name(),
                "scenario", currentScenario.get(),
                "elapsedSec", elapsed,
                "snapshot", snapshot != null ? snapshot : Map.of()
        );
    }

    @GetMapping("/metrics")
    public Mono<Map<String, Object>> getMetrics() {
        return metricsProxy.fetchMetrics();
    }

    @GetMapping("/results")
    public ResponseEntity<ScenarioResult> getResults() {
        ScenarioResult result = lastResult.get();
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }
}
