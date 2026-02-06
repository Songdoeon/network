package com.network.gateway.session;

import com.network.gateway.config.GatewayProperties;
import com.network.gateway.netty.GatewayChannelInitializer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
public class UpstreamSessionPool {

    private final GatewayProperties properties;
    private final GatewayChannelInitializer channelInitializer;
    private final List<UpstreamSession> sessions = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "session-reconnect"));

    private EventLoopGroup workerGroup;
    private Bootstrap bootstrap;

    @PostConstruct
    public void init() {
        workerGroup = new NioEventLoopGroup(2);
        bootstrap = new Bootstrap()
                .group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(channelInitializer);

        GatewayProperties.Upstream upstream = properties.getUpstream();
        for (int i = 0; i < upstream.getMaxSessions(); i++) {
            UpstreamSession session = new UpstreamSession("session-" + i);
            sessions.add(session);
            connect(session);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public UpstreamSession selectSession() {
        return sessions.stream()
                .filter(UpstreamSession::isActive)
                .min(Comparator.comparingInt(UpstreamSession::getInflightCount))
                .orElse(null);
    }

    public List<UpstreamSession> getAllSessions() {
        return List.copyOf(sessions);
    }

    public int totalInflight() {
        return sessions.stream().mapToInt(UpstreamSession::getInflightCount).sum();
    }

    private void connect(UpstreamSession session) {
        GatewayProperties.Upstream upstream = properties.getUpstream();
        String host = upstream.getHost();
        int port = upstream.getPort();

        log.info("[session={}] Connecting to {}:{}...", session.getSessionId(), host, port);

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel ch = future.channel();
                session.setChannel(ch);
                session.setState(SessionState.CONNECTED);
                log.info("[session={}] Connected to {}:{}", session.getSessionId(), host, port);

                ch.closeFuture().addListener(closeFuture -> {
                    log.warn("[session={}] Connection lost", session.getSessionId());
                    session.setState(SessionState.DOWN);
                    session.setChannel(null);
                    scheduleReconnect(session);
                });
            } else {
                log.error("[session={}] Connection failed: {}", session.getSessionId(),
                        future.cause().getMessage());
                session.setLastError(future.cause());
                session.setState(SessionState.DOWN);
                scheduleReconnect(session);
            }
        });
    }

    private void scheduleReconnect(UpstreamSession session) {
        GatewayProperties.Reconnect reconnect = properties.getUpstream().getReconnect();
        session.incrementReconnectAttempts();
        int attempts = session.getReconnectAttempts();

        long delay = Math.min(
                (long) (reconnect.getInitialDelayMs() * Math.pow(reconnect.getMultiplier(), attempts - 1)),
                reconnect.getMaxDelayMs()
        );

        log.info("[session={}] Scheduling reconnect in {}ms (attempt #{})",
                session.getSessionId(), delay, attempts);

        scheduler.schedule(() -> connect(session), delay, TimeUnit.MILLISECONDS);
    }

    public void failAllPending(UpstreamSession session, java.util.function.Consumer<UpstreamSession> callback) {
        callback.accept(session);
    }
}
