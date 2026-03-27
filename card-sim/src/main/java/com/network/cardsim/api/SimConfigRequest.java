package com.network.cardsim.api;

public record SimConfigRequest(
        Integer baseLatencyMs,
        Integer p95LatencyMs,
        Double errorRate,
        Integer disconnectEverySec,
        Double outOfOrderRate
) {}
