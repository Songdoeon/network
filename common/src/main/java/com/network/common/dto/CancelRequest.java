package com.network.common.dto;

public record CancelRequest(
        String txId,
        String merchantId,
        String idempotencyKey,
        String payload
) {
}
