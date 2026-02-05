package com.network.common.dto;

public record AuthorizeRequest(
        String merchantId,
        long amount,
        String currency,
        String idempotencyKey,
        String clientTxId,
        String payload
) {
}
