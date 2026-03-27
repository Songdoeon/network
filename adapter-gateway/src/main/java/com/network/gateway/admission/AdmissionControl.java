package com.network.gateway.admission;

import com.network.gateway.config.GatewayProperties;
import com.network.gateway.session.UpstreamSession;
import com.network.gateway.session.UpstreamSessionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class AdmissionControl {

    private final GatewayProperties properties;
    private final UpstreamSessionPool sessionPool;

    /**
     * 요청 수용 가능 여부를 판단한다.
     * 활성 세션이 없거나, 선택된 세션의 inflight이 한계에 도달하면 BUSY(503)로 거절.
     * 큐에 쌓지 않고 즉시 거절하는 정책 — 느린 상태에서 큐잉은 메모리 고갈과 연쇄 장애를 유발.
     */
    public boolean tryAdmit() {
        UpstreamSession session = sessionPool.selectSession();
        if (session == null) {
            log.warn("No active session available, rejecting request");
            return false;
        }

        int maxInflight = properties.getUpstream().getMaxInflightPerSession();
        if (session.getInflightCount() >= maxInflight) {
            log.warn("Session {} inflight limit reached ({}/{}), rejecting request",
                    session.getSessionId(), session.getInflightCount(), maxInflight);
            return false;
        }

        return true;
    }
}
