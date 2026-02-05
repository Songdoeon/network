package com.network.gateway.netty;

import com.network.common.protocol.Frame;
import com.network.common.protocol.FrameCodec;
import com.network.gateway.mux.MuxEngine;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger log = LoggerFactory.getLogger(ResponseHandler.class);

    private final MuxEngine muxEngine;

    public ResponseHandler(MuxEngine muxEngine) {
        this.muxEngine = muxEngine;
    }

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
