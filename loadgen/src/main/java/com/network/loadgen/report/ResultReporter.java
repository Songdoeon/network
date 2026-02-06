package com.network.loadgen.report;

import com.network.common.dto.TransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.HdrHistogram.Histogram;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class ResultReporter {

    private final Histogram latencyHistogram = new Histogram(60_000, 3);
    private final Map<TransactionStatus, AtomicLong> statusCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);

    public synchronized void record(TransactionStatus status, long latencyMs) {
        totalRequests.incrementAndGet();
        statusCounts.computeIfAbsent(status, k -> new AtomicLong(0)).incrementAndGet();
        if (latencyMs > 0 && latencyMs <= 60_000) {
            latencyHistogram.recordValue(latencyMs);
        }
    }

    public synchronized void printReport() {
        long total = totalRequests.get();
        if (total == 0) {
            log.info("No requests recorded.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n========== Load Test Results ==========\n");
        sb.append(String.format("Total Requests : %d\n", total));
        sb.append(String.format("p50 Latency    : %d ms\n", latencyHistogram.getValueAtPercentile(50)));
        sb.append(String.format("p95 Latency    : %d ms\n", latencyHistogram.getValueAtPercentile(95)));
        sb.append(String.format("p99 Latency    : %d ms\n", latencyHistogram.getValueAtPercentile(99)));
        sb.append(String.format("Max Latency    : %d ms\n", latencyHistogram.getMaxValue()));
        sb.append("\nStatus Distribution:\n");

        for (Map.Entry<TransactionStatus, AtomicLong> entry : statusCounts.entrySet()) {
            long count = entry.getValue().get();
            double pct = (double) count / total * 100;
            sb.append(String.format("  %-10s : %6d (%5.1f%%)\n", entry.getKey(), count, pct));
        }

        long timeouts = statusCounts.getOrDefault(TransactionStatus.TIMEOUT, new AtomicLong(0)).get();
        long errors = statusCounts.getOrDefault(TransactionStatus.ERROR, new AtomicLong(0)).get();
        long busy = statusCounts.getOrDefault(TransactionStatus.BUSY, new AtomicLong(0)).get();

        sb.append(String.format("\nTimeout Rate   : %.1f%%\n", (double) timeouts / total * 100));
        sb.append(String.format("Error Rate     : %.1f%%\n", (double) errors / total * 100));
        sb.append(String.format("Busy Rate      : %.1f%%\n", (double) busy / total * 100));
        sb.append("========================================\n");

        log.info(sb.toString());
    }

    public synchronized void reset() {
        latencyHistogram.reset();
        statusCounts.clear();
        totalRequests.set(0);
    }
}
