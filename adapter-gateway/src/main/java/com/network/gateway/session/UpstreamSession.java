package com.network.gateway.session;

import com.network.common.protocol.Frame;
import com.network.common.protocol.FrameCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class UpstreamSession {

    private static final Logger log = LoggerFactory.getLogger(UpstreamSession.class);

    private final String sessionId;
    private final AtomicReference<Channel> channel = new AtomicReference<>();
    private final AtomicInteger inflightCount = new AtomicInteger(0);
    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.DOWN);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile Throwable lastError;

    public UpstreamSession(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SessionState getState() {
        return state.get();
    }

    public void setState(SessionState newState) {
        state.set(newState);
    }

    public int getInflightCount() {
        return inflightCount.get();
    }

    public int incrementInflight() {
        return inflightCount.incrementAndGet();
    }

    public int decrementInflight() {
        return inflightCount.decrementAndGet();
    }

    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    public void incrementReconnectAttempts() {
        reconnectAttempts.incrementAndGet();
    }

    public void resetReconnectAttempts() {
        reconnectAttempts.set(0);
    }

    public Throwable getLastError() {
        return lastError;
    }

    public void setLastError(Throwable lastError) {
        this.lastError = lastError;
    }

    public void setChannel(Channel ch) {
        this.channel.set(ch);
        if (ch != null && ch.isActive()) {
            state.set(SessionState.CONNECTED);
            resetReconnectAttempts();
        }
    }

    public Channel getChannel() {
        return channel.get();
    }

    public boolean isActive() {
        Channel ch = channel.get();
        return ch != null && ch.isActive() && state.get() == SessionState.CONNECTED;
    }

    public boolean write(Frame frame) {
        Channel ch = channel.get();
        if (ch == null || !ch.isActive()) {
            log.warn("[session={}] Cannot write, channel inactive", sessionId);
            return false;
        }
        ByteBuf encoded = FrameCodec.encode(frame);
        ch.writeAndFlush(encoded);
        return true;
    }
}
