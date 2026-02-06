package com.network.loadgen.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "loadgen")
public class LoadGenProperties {

    private String gatewayUrl = "http://localhost:8080";
    private int concurrentUsers = 100;
    private int durationSeconds = 30;
    private int rampUpSeconds = 5;
}
