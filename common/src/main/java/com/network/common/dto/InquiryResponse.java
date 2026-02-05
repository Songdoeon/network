package com.network.common.dto;

public record InquiryResponse(
        String txId,
        TransactionStatus status,
        String reasonCode,
        long latencyMs
) {
}
