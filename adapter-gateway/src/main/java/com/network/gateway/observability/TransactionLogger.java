package com.network.gateway.observability;

import com.network.common.dto.AuthorizeResponse;
import com.network.common.dto.TransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionLogger {

    public void logRequest(String txId, String correlationId, String merchantId, String idempotencyKey) {
        MDC.put("txId", txId);
        MDC.put("correlationId", correlationId);
        MDC.put("merchantId", merchantId);
        if (idempotencyKey != null) {
            MDC.put("idempotencyKey", idempotencyKey);
        }
        log.info("Transaction started");
    }

    public void logResponse(AuthorizeResponse response) {
        MDC.put("txId", response.txId());
        MDC.put("status", response.status().name());
        MDC.put("latencyMs", String.valueOf(response.latencyMs()));
        if (response.upstreamSessionId() != null) {
            MDC.put("sessionId", response.upstreamSessionId());
        }

        if (response.status() == TransactionStatus.APPROVED) {
            log.info("Transaction completed: {}", response.status());
        } else {
            log.warn("Transaction completed: {} (reason={})", response.status(), response.reasonCode());
        }

        MDC.clear();
    }

    public void logBusyReject(String reason) {
        log.warn("Request rejected: BUSY ({})", reason);
    }
}
