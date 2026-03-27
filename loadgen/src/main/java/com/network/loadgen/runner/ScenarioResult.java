package com.network.loadgen.runner;

import java.util.Map;

public record ScenarioResult(
        String scenario,
        long totalRequests,
        Map<String, Long> statusCounts,
        long p50Ms,
        long p95Ms,
        long p99Ms,
        long maxMs,
        double timeoutRate,
        double errorRate,
        double busyRate
) {}
