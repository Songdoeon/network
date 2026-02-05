package com.network.common.dto;

public record CancelResponse(
        String txId,
        TransactionStatus status,
        String reasonCode,
        long latencyMs
) {
}
