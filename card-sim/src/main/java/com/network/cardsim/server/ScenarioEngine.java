package com.network.cardsim.server;

import com.network.cardsim.config.SimulatorProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScenarioEngine {

    private static final Logger log = LoggerFactory.getLogger(ScenarioEngine.class);

    private final SimulatorProperties properties;
    private final CardSimChannelInitializer channelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverFuture;

    public ScenarioEngine(SimulatorProperties properties, CardSimChannelInitializer channelInitializer) {
        this.properties = properties;
        this.channelInitializer = channelInitializer;
    }

    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(channelInitializer)
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true);

        try {
            serverFuture = bootstrap.bind(properties.getPort()).sync();
            log.info("Card Network Simulator started on port {}", properties.getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to start card-sim server", e);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Shutting down Card Network Simulator...");
        if (serverFuture != null) {
            serverFuture.channel().close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }
}
