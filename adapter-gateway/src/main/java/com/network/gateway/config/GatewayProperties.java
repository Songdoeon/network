package com.network.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Upstream upstream = new Upstream();
    private Idempotency idempotency = new Idempotency();

    @Getter
    @Setter
    public static class Upstream {
        private String host = "localhost";
        private int port = 9090;
        private int maxSessions = 2;
        private int maxInflightPerSession = 2000;
        private int maxQueueDepth = 20000;
        private int requestTimeoutMs = 2000;
        private int queueWaitMaxMs = 300;
        private Reconnect reconnect = new Reconnect();
    }

    @Getter
    @Setter
    public static class Reconnect {
        private long initialDelayMs = 100;
        private long maxDelayMs = 5000;
        private double multiplier = 2.0;
    }

    @Getter
    @Setter
    public static class Idempotency {
        private int ttlSeconds = 300;
    }
}
