package com.network.gateway.transaction;

import com.network.common.dto.TransactionStatus;
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
public class TransactionLog {

    private final GatewayProperties properties;
    private final ConcurrentMap<String, TransactionEntry> store = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleaner;

    public record TransactionEntry(
            String txId,
            TransactionStatus status,
            String reasonCode,
            long latencyMs,
            String sessionId,
            String merchantId,
            Instant completedAt
    ) {}

    @PostConstruct
    public void init() {
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "txlog-cleaner"));
        cleaner.scheduleAtFixedRate(this::evictExpired, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (cleaner != null) {
            cleaner.shutdown();
        }
    }

    public void record(String txId, TransactionStatus status, String reasonCode,
                        long latencyMs, String sessionId, String merchantId) {
        store.put(txId, new TransactionEntry(txId, status, reasonCode, latencyMs, sessionId, merchantId, Instant.now()));
    }

    public TransactionEntry get(String txId) {
        return store.get(txId);
    }

    private void evictExpired() {
        int ttlSeconds = properties.getIdempotency().getTtlSeconds();
        Instant cutoff = Instant.now().minusSeconds(ttlSeconds);
        int evicted = 0;
        for (Map.Entry<String, TransactionEntry> entry : store.entrySet()) {
            if (entry.getValue().completedAt().isBefore(cutoff)) {
                store.remove(entry.getKey());
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("Evicted {} expired transaction log entries", evicted);
        }
    }
}
