package com.network.gateway.netty;

import com.network.common.protocol.Frame;
import com.network.common.protocol.FrameCodec;
import com.network.gateway.mux.MuxEngine;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final MuxEngine muxEngine;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        try {
            Frame frame = FrameCodec.decode(msg);
            log.debug("Received response: correlationId={}, type={}", frame.correlationId(), frame.messageType());
            muxEngine.completeRequest(frame);
        } catch (Exception e) {
            log.error("Failed to decode response frame", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Channel exception: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
