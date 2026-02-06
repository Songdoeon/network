package com.network.gateway.admission;

import com.network.gateway.config.GatewayProperties;
import com.network.gateway.session.UpstreamSession;
import com.network.gateway.session.UpstreamSessionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Component
public class AdmissionControl {

    private final GatewayProperties properties;
    private final UpstreamSessionPool sessionPool;
    private final AtomicInteger queueDepth = new AtomicInteger(0);

    /**
     * 요청 수용 가능 여부를 판단한다.
     * @return 수용 가능하면 true, 거절(BUSY)이면 false
     */
    public boolean tryAdmit() {
        GatewayProperties.Upstream upstream = properties.getUpstream();

        // 세션별 inflight 합계 체크
        UpstreamSession session = sessionPool.selectSession();
        if (session == null) {
            log.warn("No active session available, rejecting request");
            return false;
        }

        if (session.getInflightCount() >= upstream.getMaxInflightPerSession()) {
            // 큐 대기 가능한지 체크
            if (queueDepth.get() >= upstream.getMaxQueueDepth()) {
                log.warn("Queue depth limit reached ({}), rejecting request", upstream.getMaxQueueDepth());
                return false;
            }
        }

        return true;
    }

    public int incrementQueue() {
        return queueDepth.incrementAndGet();
    }

    public int decrementQueue() {
        return queueDepth.decrementAndGet();
    }

    public int getQueueDepth() {
        return queueDepth.get();
    }
}
