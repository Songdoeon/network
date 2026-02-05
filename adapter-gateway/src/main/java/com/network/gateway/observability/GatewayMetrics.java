package com.network.gateway.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GatewayMetrics {

    private final Timer txLatency;
    private final Counter timeoutCounter;
    private final Counter errorCounter;
    private final Counter busyRejectCounter;
    private final Counter lateResponseCounter;
    private final Counter reconnectCounter;
    private final Map<String, AtomicInteger> inflightGauges = new ConcurrentHashMap<>();
    private final AtomicInteger queueDepthValue = new AtomicInteger(0);

    public GatewayMetrics(MeterRegistry registry) {
        this.txLatency = Timer.builder("tx.latency")
                .description("Transaction end-to-end latency")
                .publishPercentileHistogram()
                .register(registry);

        this.timeoutCounter = Counter.builder("tx.timeout.count")
                .description("Number of timed out transactions")
                .register(registry);

        this.errorCounter = Counter.builder("tx.error.count")
                .description("Number of errored transactions")
                .register(registry);

        this.busyRejectCounter = Counter.builder("tx.busy.reject.count")
                .description("Number of BUSY rejections")
                .register(registry);

        this.lateResponseCounter = Counter.builder("tx.late.response.count")
                .description("Number of late responses after timeout")
                .register(registry);

        this.reconnectCounter = Counter.builder("session.reconnect.count")
                .description("Number of session reconnect attempts")
                .register(registry);

        registry.gauge("queue.depth", queueDepthValue);
    }

    public void recordLatency(long latencyMs) {
        txLatency.record(Duration.ofMillis(latencyMs));
    }

    public void incrementTimeout() {
        timeoutCounter.increment();
    }

    public void incrementError() {
        errorCounter.increment();
    }

    public void incrementBusyReject() {
        busyRejectCounter.increment();
    }

    public void incrementLateResponse() {
        lateResponseCounter.increment();
    }

    public void incrementReconnect() {
        reconnectCounter.increment();
    }

    public void setInflight(String sessionId, int count) {
        inflightGauges.computeIfAbsent(sessionId, id -> new AtomicInteger(0)).set(count);
    }

    public void setQueueDepth(int depth) {
        queueDepthValue.set(depth);
    }

    public void registerInflightGauge(String sessionId, MeterRegistry registry) {
        AtomicInteger gauge = inflightGauges.computeIfAbsent(sessionId, id -> new AtomicInteger(0));
        registry.gauge("session.inflight", io.micrometer.core.instrument.Tags.of("sessionId", sessionId), gauge);
    }
}
