package com.network.gateway.support;

import com.network.common.dto.TransactionStatus;
import com.network.common.protocol.Frame;
import com.network.common.protocol.FrameCodec;
import com.network.common.protocol.MessageType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class MockCardSimServer {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private final ChannelGroup clientChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile Function<Frame, Frame> responseBuilder = MockCardSimServer::defaultResponse;
    private volatile Duration responseDelay = Duration.ZERO;

    public int start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(2);

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        clientChannels.add(ch);
                        ch.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 0))
                                .addLast(new MockHandler());
                    }
                });

        serverChannel = bootstrap.bind(0).sync().channel();
        return ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
    }

    public void stop() {
        scheduler.shutdown();
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

    public void setResponseBuilder(Function<Frame, Frame> builder) {
        this.responseBuilder = builder;
    }

    public void setResponseDelay(Duration delay) {
        this.responseDelay = delay;
    }

    public void disconnectAll() {
        clientChannels.close();
    }

    private static Frame defaultResponse(Frame request) {
        MessageType responseType = switch (request.messageType()) {
            case AUTH_REQ -> MessageType.AUTH_RES;
            case CANCEL_REQ -> MessageType.CANCEL_RES;
            case INQUIRY_REQ -> MessageType.INQUIRY_RES;
            default -> MessageType.AUTH_RES;
        };
        byte[] body = (TransactionStatus.APPROVED.name() + "|OK").getBytes(StandardCharsets.UTF_8);
        return new Frame(request.correlationId(), responseType, body);
    }

    private class MockHandler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            Frame request = FrameCodec.decode(msg);
            Frame response = responseBuilder.apply(request);
            if (response == null) {
                return; // don't respond (simulate hang)
            }

            long delayMs = responseDelay.toMillis();
            if (delayMs > 0) {
                scheduler.schedule(() -> {
                    if (ctx.channel().isActive()) {
                        ctx.writeAndFlush(FrameCodec.encode(response));
                    }
                }, delayMs, TimeUnit.MILLISECONDS);
            } else {
                ctx.writeAndFlush(FrameCodec.encode(response));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
