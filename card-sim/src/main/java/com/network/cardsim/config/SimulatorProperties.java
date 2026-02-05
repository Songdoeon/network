package com.network.cardsim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cardsim")
public class SimulatorProperties {

    private int port = 9090;
    private int baseLatencyMs = 30;
    private int p95LatencyMs = 100;
    private double errorRate = 0.01;
    private int disconnectEverySec = 0;
    private double outOfOrderRate = 0.0;

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public int getBaseLatencyMs() { return baseLatencyMs; }
    public void setBaseLatencyMs(int baseLatencyMs) { this.baseLatencyMs = baseLatencyMs; }
    public int getP95LatencyMs() { return p95LatencyMs; }
    public void setP95LatencyMs(int p95LatencyMs) { this.p95LatencyMs = p95LatencyMs; }
    public double getErrorRate() { return errorRate; }
    public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
    public int getDisconnectEverySec() { return disconnectEverySec; }
    public void setDisconnectEverySec(int disconnectEverySec) { this.disconnectEverySec = disconnectEverySec; }
    public double getOutOfOrderRate() { return outOfOrderRate; }
    public void setOutOfOrderRate(double outOfOrderRate) { this.outOfOrderRate = outOfOrderRate; }
}
