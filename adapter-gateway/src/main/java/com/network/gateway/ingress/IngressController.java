package com.network.gateway.ingress;

import com.network.common.dto.*;
import com.network.common.protocol.MessageType;
import com.network.gateway.admission.AdmissionControl;
import com.network.gateway.idempotency.IdempotencyStore;
import com.network.gateway.mux.MuxEngine;
import com.network.gateway.observability.GatewayMetrics;
import com.network.gateway.observability.TransactionLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/v1")
public class IngressController {

    private static final Logger log = LoggerFactory.getLogger(IngressController.class);

    private final MuxEngine muxEngine;
    private final AdmissionControl admissionControl;
    private final IdempotencyStore idempotencyStore;
    private final GatewayMetrics metrics;
    private final TransactionLogger txLogger;

    public IngressController(MuxEngine muxEngine, AdmissionControl admissionControl,
                             IdempotencyStore idempotencyStore, GatewayMetrics metrics,
                             TransactionLogger txLogger) {
        this.muxEngine = muxEngine;
        this.admissionControl = admissionControl;
        this.idempotencyStore = idempotencyStore;
        this.metrics = metrics;
        this.txLogger = txLogger;
    }

    @PostMapping("/authorize")
    public ResponseEntity<AuthorizeResponse> authorize(@RequestBody AuthorizeRequest request) {
        // 1. 멱등성 체크
        String idempotencyKey = request.idempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            IdempotencyStore.IdempotencyEntry existing = idempotencyStore.getIfPresent(idempotencyKey);
            if (existing != null) {
                if (existing.isCompleted()) {
                    return ResponseEntity.ok(existing.getResponse());
                }
                // 처리 중인 요청에 attach
                try {
                    AuthorizeResponse response = existing.getFuture().get(2, TimeUnit.SECONDS);
                    return ResponseEntity.ok(response);
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                            .body(new AuthorizeResponse(null, TransactionStatus.TIMEOUT, "IDEMPOTENT_WAIT_TIMEOUT", 0, null));
                }
            }
        }

        // 2. Admission control
        if (!admissionControl.tryAdmit()) {
            metrics.incrementBusyReject();
            txLogger.logBusyReject("inflight/queue limit exceeded");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AuthorizeResponse(null, TransactionStatus.BUSY, "CAPACITY_EXCEEDED", 0, null));
        }

        // 3. 요청 제출
        CompletableFuture<AuthorizeResponse> future = muxEngine.submit(request, MessageType.AUTH_REQ, idempotencyKey);

        // 멱등성 등록
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyStore.putIfAbsent(idempotencyKey, future);
        }

        // 4. 동기 응답 대기
        try {
            AuthorizeResponse response = future.get(3, TimeUnit.SECONDS);
            txLogger.logResponse(response);

            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                idempotencyStore.markCompleted(idempotencyKey, response);
            }

            return switch (response.status()) {
                case APPROVED, DECLINED -> ResponseEntity.ok(response);
                case TIMEOUT -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
                case BUSY -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
                case ERROR -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            };
        } catch (Exception e) {
            log.error("Failed to get response", e);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new AuthorizeResponse(null, TransactionStatus.TIMEOUT, "GATEWAY_TIMEOUT", 0, null));
        }
    }

    @PostMapping("/cancel")
    public ResponseEntity<AuthorizeResponse> cancel(@RequestBody CancelRequest request) {
        AuthorizeRequest wrapped = new AuthorizeRequest(
                request.merchantId(), 0, null, request.idempotencyKey(),
                request.txId(), request.payload());

        if (!admissionControl.tryAdmit()) {
            metrics.incrementBusyReject();
            txLogger.logBusyReject("inflight/queue limit exceeded");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AuthorizeResponse(null, TransactionStatus.BUSY, "CAPACITY_EXCEEDED", 0, null));
        }

        CompletableFuture<AuthorizeResponse> future = muxEngine.submit(wrapped, MessageType.CANCEL_REQ, request.idempotencyKey());

        try {
            AuthorizeResponse response = future.get(3, TimeUnit.SECONDS);
            txLogger.logResponse(response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new AuthorizeResponse(null, TransactionStatus.TIMEOUT, "GATEWAY_TIMEOUT", 0, null));
        }
    }

    @GetMapping("/inquiry/{txId}")
    public ResponseEntity<InquiryResponse> inquiry(@PathVariable String txId) {
        // MVP: inquiry는 현재 pending 상태만 확인
        return ResponseEntity.ok(new InquiryResponse(txId, TransactionStatus.APPROVED, "OK", 0));
    }
}
