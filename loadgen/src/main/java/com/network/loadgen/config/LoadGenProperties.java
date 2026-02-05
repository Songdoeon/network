package com.network.loadgen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loadgen")
public class LoadGenProperties {

    private String gatewayUrl = "http://localhost:8080";
    private int concurrentUsers = 100;
    private int durationSeconds = 30;
    private int rampUpSeconds = 5;

    public String getGatewayUrl() { return gatewayUrl; }
    public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }
    public int getConcurrentUsers() { return concurrentUsers; }
    public void setConcurrentUsers(int concurrentUsers) { this.concurrentUsers = concurrentUsers; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public int getRampUpSeconds() { return rampUpSeconds; }
    public void setRampUpSeconds(int rampUpSeconds) { this.rampUpSeconds = rampUpSeconds; }
}
