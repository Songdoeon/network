package com.network.loadgen.report;

import com.network.common.dto.TransactionStatus;
import com.network.loadgen.runner.ScenarioResult;
import lombok.extern.slf4j.Slf4j;
import org.HdrHistogram.Histogram;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ResultReporter {

    private final Histogram latencyHistogram = new Histogram(60_000, 3);
    private final Map<TransactionStatus, AtomicLong> statusCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);

    public record TimelineSnapshot(int offsetSec, double inflight, double queueDepth) {}

    private final List<TimelineSnapshot> timeline = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService sampler;
    private long samplingStartMs;

    public synchronized void record(TransactionStatus status, long latencyMs) {
        totalRequests.incrementAndGet();
        statusCounts.computeIfAbsent(status, k -> new AtomicLong(0)).incrementAndGet();
        if (latencyMs > 0 && latencyMs <= 60_000) {
            latencyHistogram.recordValue(latencyMs);
        }
    }

    public void startSampling(String gatewayUrl) {
        timeline.clear();
        samplingStartMs = System.currentTimeMillis();
        WebClient client = WebClient.create(gatewayUrl);
        sampler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "metrics-sampler"));
        sampler.scheduleAtFixedRate(() -> {
            try {
                String body = client.get()
                        .uri("/actuator/prometheus")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(2))
                        .block();
                if (body != null) {
                    double inflight = parseMetric(body, "session_inflight");
                    double queueDepth = parseMetric(body, "queue_depth");
                    int offsetSec = (int) ((System.currentTimeMillis() - samplingStartMs) / 1000);
                    timeline.add(new TimelineSnapshot(offsetSec, inflight, queueDepth));
                }
            } catch (Exception e) {
                log.debug("Sampling failed: {}", e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void stopSampling() {
        if (sampler != null) {
            sampler.shutdown();
            sampler = null;
        }
    }

    public synchronized void printReport(String scenarioName) {
        long total = totalRequests.get();
        if (total == 0) {
            log.info("No requests recorded.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\n========== Load Test Results: %s ==========\n", scenarioName));
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

        if (!timeline.isEmpty()) {
            sb.append("\nInflight / Queue Depth Timeline (5s intervals):\n");
            sb.append(String.format("  %-8s  %-12s  %-12s\n", "Time(s)", "Inflight", "QueueDepth"));
            for (TimelineSnapshot snap : timeline) {
                sb.append(String.format("  %-8d  %-12.0f  %-12.0f\n",
                        snap.offsetSec(), snap.inflight(), snap.queueDepth()));
            }
        }

        sb.append("========================================\n");

        log.info(sb.toString());
    }

    public synchronized void printReport() {
        printReport("DEFAULT");
    }

    public synchronized ScenarioResult toResult(String scenarioName) {
        long total = totalRequests.get();
        Map<String, Long> counts = new LinkedHashMap<>();
        statusCounts.forEach((k, v) -> counts.put(k.name(), v.get()));

        long timeouts = statusCounts.getOrDefault(TransactionStatus.TIMEOUT, new AtomicLong(0)).get();
        long errors = statusCounts.getOrDefault(TransactionStatus.ERROR, new AtomicLong(0)).get();
        long busy = statusCounts.getOrDefault(TransactionStatus.BUSY, new AtomicLong(0)).get();

        return new ScenarioResult(
                scenarioName, total, counts,
                total > 0 ? latencyHistogram.getValueAtPercentile(50) : 0,
                total > 0 ? latencyHistogram.getValueAtPercentile(95) : 0,
                total > 0 ? latencyHistogram.getValueAtPercentile(99) : 0,
                total > 0 ? latencyHistogram.getMaxValue() : 0,
                total > 0 ? (double) timeouts / total * 100 : 0,
                total > 0 ? (double) errors / total * 100 : 0,
                total > 0 ? (double) busy / total * 100 : 0
        );
    }

    public synchronized ScenarioResult getSnapshot() {
        return toResult("LIVE");
    }

    public List<TimelineSnapshot> getTimeline() {
        return List.copyOf(timeline);
    }

    public synchronized void reset() {
        latencyHistogram.reset();
        statusCounts.clear();
        totalRequests.set(0);
        timeline.clear();
    }

    public static double parseMetric(String body, String metricName) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(metricName) + "\\b.*\\s([\\d.]+)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(body);
        double sum = 0;
        int count = 0;
        while (matcher.find()) {
            try {
                sum += Double.parseDouble(matcher.group(1));
                count++;
            } catch (NumberFormatException ignored) {}
        }
        return count > 0 ? sum : 0;
    }
}
