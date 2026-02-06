package com.network.cardsim.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "cardsim")
public class SimulatorProperties {

    private int port = 9090;
    private int baseLatencyMs = 30;
    private int p95LatencyMs = 100;
    private double errorRate = 0.01;
    private int disconnectEverySec = 0;
    private double outOfOrderRate = 0.0;
}
