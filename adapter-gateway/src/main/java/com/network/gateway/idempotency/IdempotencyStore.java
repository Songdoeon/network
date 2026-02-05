package com.network.gateway.idempotency;

import com.network.common.dto.AuthorizeResponse;
import com.network.gateway.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Component
public class IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyStore.class);

    private final GatewayProperties properties;
    private final ConcurrentMap<String, IdempotencyEntry> store = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleaner;

    public IdempotencyStore(GatewayProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "idempotency-cleaner"));
        cleaner.scheduleAtFixedRate(this::evictExpired, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (cleaner != null) {
            cleaner.shutdown();
        }
    }

    /**
     * 이미 처리 중이거나 완료된 요청의 결과를 반환한다.
     * @return null이면 신규 요청, 값이 있으면 기존 결과 또는 진행 중인 future
     */
    public IdempotencyEntry getIfPresent(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return store.get(idempotencyKey);
    }

    public IdempotencyEntry putIfAbsent(String idempotencyKey, CompletableFuture<AuthorizeResponse> future) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        IdempotencyEntry newEntry = new IdempotencyEntry(future, Instant.now());
        return store.putIfAbsent(idempotencyKey, newEntry);
    }

    public void markCompleted(String idempotencyKey, AuthorizeResponse response) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        IdempotencyEntry entry = store.get(idempotencyKey);
        if (entry != null) {
            entry.setResponse(response);
        }
    }

    private void evictExpired() {
        int ttlSeconds = properties.getIdempotency().getTtlSeconds();
        Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
        int evicted = 0;
        for (Map.Entry<String, IdempotencyEntry> entry : store.entrySet()) {
            if (entry.getValue().getCreatedAt().isBefore(cutoff)) {
                store.remove(entry.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("Evicted {} expired idempotency entries", evicted);
        }
    }

    public static class IdempotencyEntry {
        private final CompletableFuture<AuthorizeResponse> future;
        private final Instant createdAt;
        private volatile AuthorizeResponse response;

        public IdempotencyEntry(CompletableFuture<AuthorizeResponse> future, Instant createdAt) {
            this.future = future;
            this.createdAt = createdAt;
        }

        public CompletableFuture<AuthorizeResponse> getFuture() { return future; }
        public Instant getCreatedAt() { return createdAt; }
        public AuthorizeResponse getResponse() { return response; }
        public void setResponse(AuthorizeResponse response) { this.response = response; }
        public boolean isCompleted() { return response != null; }
    }
}
