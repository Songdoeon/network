package com.network.common.dto;

public record AuthorizeResponse(
        String txId,
        TransactionStatus status,
        String reasonCode,
        long latencyMs,
        String upstreamSessionId
) {
}
