package com.network.gateway.idempotency;

import com.network.common.dto.AuthorizeResponse;
import com.network.gateway.config.GatewayProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class IdempotencyStore {

    private final GatewayProperties properties;
    private final ConcurrentMap<String, IdempotencyEntry> store = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleaner;

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

    /**
     * 멱등성 키를 등록한다. 이미 존재하면 기존 future를 반환, 신규면 null.
     * ConcurrentHashMap.putIfAbsent로 원자적 등록 보장 → 동시 요청 시 하나만 실행.
     */
    public CompletableFuture<AuthorizeResponse> putIfAbsent(String idempotencyKey, CompletableFuture<AuthorizeResponse> future) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        IdempotencyEntry newEntry = new IdempotencyEntry(future, Instant.now());
        IdempotencyEntry existing = store.putIfAbsent(idempotencyKey, newEntry);
        return existing != null ? existing.getFuture() : null;
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
        int sizeBefore = store.size();
        store.entrySet().removeIf(entry -> entry.getValue().getCreatedAt().isBefore(cutoff));
        int evicted = sizeBefore - store.size();
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
