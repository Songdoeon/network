package com.network.cardsim.api;

import com.network.cardsim.config.SimulatorProperties;

public record SimConfigResponse(
        int baseLatencyMs,
        int p95LatencyMs,
        double errorRate,
        int disconnectEverySec,
        double outOfOrderRate
) {
    public static SimConfigResponse from(SimulatorProperties props) {
        return new SimConfigResponse(
                props.getBaseLatencyMs(),
                props.getP95LatencyMs(),
                props.getErrorRate(),
                props.getDisconnectEverySec(),
                props.getOutOfOrderRate()
        );
    }
}
