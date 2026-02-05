package com.network.gateway.mux;

import com.network.common.dto.AuthorizeResponse;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class PendingRequest {

    private final String correlationId;
    private final String txId;
    private final Instant createdAt;
    private final Instant deadlineAt;
    private final CompletableFuture<AuthorizeResponse> future;
    private final String sessionId;
    private final String idempotencyKey;

    public PendingRequest(String correlationId, String txId, Instant createdAt,
                          Instant deadlineAt, String sessionId, String idempotencyKey) {
        this.correlationId = correlationId;
        this.txId = txId;
        this.createdAt = createdAt;
        this.deadlineAt = deadlineAt;
        this.future = new CompletableFuture<>();
        this.sessionId = sessionId;
        this.idempotencyKey = idempotencyKey;
    }

    public String getCorrelationId() { return correlationId; }
    public String getTxId() { return txId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public CompletableFuture<AuthorizeResponse> getFuture() { return future; }
    public String getSessionId() { return sessionId; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
