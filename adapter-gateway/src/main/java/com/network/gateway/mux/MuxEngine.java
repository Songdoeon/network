package com.network.gateway.mux;

import com.network.common.dto.AuthorizeRequest;
import com.network.common.dto.AuthorizeResponse;
import com.network.common.dto.TransactionStatus;
import com.network.common.protocol.CorrelationIdGenerator;
import com.network.common.protocol.Frame;
import com.network.common.protocol.MessageType;
import com.network.gateway.config.GatewayProperties;
import com.network.gateway.observability.GatewayMetrics;
import com.network.gateway.session.UpstreamSession;
import com.network.gateway.session.UpstreamSessionPool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class MuxEngine {

    private final GatewayProperties properties;
    private final UpstreamSessionPool sessionPool;
    private final GatewayMetrics metrics;
    private final PendingMap pendingMap = new PendingMap();
    private final CorrelationIdGenerator corrIdGen = new CorrelationIdGenerator();
    private ScheduledExecutorService timeoutScheduler;

    @PostConstruct
    public void init() {
        timeoutScheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "mux-timeout");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdown() {
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdown();
        }
    }

    /**
     * 요청을 멀티플렉싱 엔진에 제출한다.
     * 세션 선택 → pending 등록 → encode + write → timeout 스케줄
     */
    public CompletableFuture<AuthorizeResponse> submit(AuthorizeRequest request, MessageType messageType,
                                                        String idempotencyKey) {
        String txId = UUID.randomUUID().toString().substring(0, 8);
        String correlationId = corrIdGen.next();

        MDC.put("txId", txId);
        MDC.put("correlationId", correlationId);

        UpstreamSession session = sessionPool.selectSession();
        if (session == null) {
            log.warn("[txId={}] No active upstream session", txId);
            metrics.incrementError();
            return CompletableFuture.completedFuture(
                    new AuthorizeResponse(txId, TransactionStatus.ERROR, "NO_UPSTREAM", 0, null));
        }

        Instant now = Instant.now();
        int timeoutMs = properties.getUpstream().getRequestTimeoutMs();
        Instant deadline = now.plusMillis(timeoutMs);

        PendingRequest pending = new PendingRequest(
                correlationId, txId, now, deadline, session.getSessionId(), idempotencyKey);
        pendingMap.put(correlationId, pending);

        session.incrementInflight();
        metrics.setInflight(session.getSessionId(), session.getInflightCount());

        // 프레임 생성 및 전송
        byte[] body = serializeRequest(request);
        Frame frame = new Frame(correlationId, messageType, body);

        boolean written = session.write(frame);
        if (!written) {
            pendingMap.remove(correlationId);
            session.decrementInflight();
            metrics.setInflight(session.getSessionId(), session.getInflightCount());
            log.error("[txId={}] Failed to write to session {}", txId, session.getSessionId());
            metrics.incrementError();
            return CompletableFuture.completedFuture(
                    new AuthorizeResponse(txId, TransactionStatus.ERROR, "WRITE_FAILED", 0, session.getSessionId()));
        }

        log.debug("[txId={}] Sent to session={}, correlationId={}", txId, session.getSessionId(), correlationId);

        // timeout 스케줄
        timeoutScheduler.schedule(
                () -> handleTimeout(correlationId),
                timeoutMs,
                TimeUnit.MILLISECONDS
        );

        return pending.getFuture();
    }

    /**
     * 응답 프레임을 수신하여 pending 요청과 매칭한다.
     */
    public void completeRequest(Frame responseFrame) {
        String correlationId = responseFrame.correlationId();
        PendingRequest pending = pendingMap.remove(correlationId);

        if (pending == null) {
            // late response: timeout 이후 도착
            log.warn("[correlationId={}] Late response received (already timed out or completed)", correlationId);
            metrics.incrementLateResponse();
            return;
        }

        long latencyMs = Duration.between(pending.getCreatedAt(), Instant.now()).toMillis();
        String sessionId = pending.getSessionId();

        // inflight 감소
        for (UpstreamSession session : sessionPool.getAllSessions()) {
            if (session.getSessionId().equals(sessionId)) {
                session.decrementInflight();
                metrics.setInflight(sessionId, session.getInflightCount());
                break;
            }
        }

        // 응답 파싱
        TransactionStatus status = parseStatus(responseFrame.body());
        String reasonCode = parseReasonCode(responseFrame.body());

        AuthorizeResponse response = new AuthorizeResponse(
                pending.getTxId(), status, reasonCode, latencyMs, sessionId);

        metrics.recordLatency(latencyMs);

        log.debug("[txId={}] Completed: status={}, latency={}ms", pending.getTxId(), status, latencyMs);

        pending.getFuture().complete(response);
    }

    /**
     * 세션 disconnection 시 해당 세션의 모든 pending을 fail-fast 처리한다.
     */
    public void failPendingForSession(String sessionId) {
        pendingMap.forEach((corrId, pending) -> {
            if (sessionId.equals(pending.getSessionId())) {
                PendingRequest removed = pendingMap.remove(corrId);
                if (removed != null) {
                    long latencyMs = Duration.between(removed.getCreatedAt(), Instant.now()).toMillis();
                    removed.getFuture().complete(
                            new AuthorizeResponse(removed.getTxId(), TransactionStatus.ERROR,
                                    "UPSTREAM_DOWN", latencyMs, sessionId));
                    metrics.incrementError();
                }
            }
        });
    }

    public int getPendingCount() {
        return pendingMap.size();
    }

    private void handleTimeout(String correlationId) {
        PendingRequest pending = pendingMap.remove(correlationId);
        if (pending == null) {
            return; // 이미 완료됨
        }

        long latencyMs = Duration.between(pending.getCreatedAt(), Instant.now()).toMillis();
        String sessionId = pending.getSessionId();

        // inflight 감소
        for (UpstreamSession session : sessionPool.getAllSessions()) {
            if (session.getSessionId().equals(sessionId)) {
                session.decrementInflight();
                metrics.setInflight(sessionId, session.getInflightCount());
                break;
            }
        }

        log.warn("[txId={}] Timeout after {}ms (correlationId={})", pending.getTxId(), latencyMs, correlationId);

        metrics.incrementTimeout();
        metrics.recordLatency(latencyMs);

        pending.getFuture().complete(
                new AuthorizeResponse(pending.getTxId(), TransactionStatus.TIMEOUT,
                        "REQUEST_TIMEOUT", latencyMs, sessionId));
    }

    private byte[] serializeRequest(AuthorizeRequest request) {
        // 간이 직렬화: merchantId|amount|currency|clientTxId|payload
        String data = String.join("|",
                nullSafe(request.merchantId()),
                String.valueOf(request.amount()),
                nullSafe(request.currency()),
                nullSafe(request.clientTxId()),
                nullSafe(request.payload()));
        return data.getBytes(StandardCharsets.UTF_8);
    }

    private TransactionStatus parseStatus(byte[] body) {
        if (body == null || body.length == 0) {
            return TransactionStatus.ERROR;
        }
        String data = new String(body, StandardCharsets.UTF_8);
        String[] parts = data.split("\\|", -1);
        if (parts.length > 0) {
            try {
                return TransactionStatus.valueOf(parts[0]);
            } catch (IllegalArgumentException e) {
                return TransactionStatus.ERROR;
            }
        }
        return TransactionStatus.ERROR;
    }

    private String parseReasonCode(byte[] body) {
        if (body == null || body.length == 0) {
            return "UNKNOWN";
        }
        String data = new String(body, StandardCharsets.UTF_8);
        String[] parts = data.split("\\|", -1);
        if (parts.length > 1) {
            return parts[1];
        }
        return "OK";
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
