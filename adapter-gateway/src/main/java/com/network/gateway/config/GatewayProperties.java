package com.network.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Upstream upstream = new Upstream();
    private Idempotency idempotency = new Idempotency();

    public Upstream getUpstream() {
        return upstream;
    }

    public void setUpstream(Upstream upstream) {
        this.upstream = upstream;
    }

    public Idempotency getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(Idempotency idempotency) {
        this.idempotency = idempotency;
    }

    public static class Upstream {
        private String host = "localhost";
        private int port = 9090;
        private int maxSessions = 2;
        private int maxInflightPerSession = 2000;
        private int maxQueueDepth = 20000;
        private int requestTimeoutMs = 2000;
        private int queueWaitMaxMs = 300;
        private Reconnect reconnect = new Reconnect();

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getMaxSessions() { return maxSessions; }
        public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
        public int getMaxInflightPerSession() { return maxInflightPerSession; }
        public void setMaxInflightPerSession(int maxInflightPerSession) { this.maxInflightPerSession = maxInflightPerSession; }
        public int getMaxQueueDepth() { return maxQueueDepth; }
        public void setMaxQueueDepth(int maxQueueDepth) { this.maxQueueDepth = maxQueueDepth; }
        public int getRequestTimeoutMs() { return requestTimeoutMs; }
        public void setRequestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
        public int getQueueWaitMaxMs() { return queueWaitMaxMs; }
        public void setQueueWaitMaxMs(int queueWaitMaxMs) { this.queueWaitMaxMs = queueWaitMaxMs; }
        public Reconnect getReconnect() { return reconnect; }
        public void setReconnect(Reconnect reconnect) { this.reconnect = reconnect; }
    }

    public static class Reconnect {
        private long initialDelayMs = 100;
        private long maxDelayMs = 5000;
        private double multiplier = 2.0;

        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
    }

    public static class Idempotency {
        private int ttlSeconds = 300;

        public int getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }
}
