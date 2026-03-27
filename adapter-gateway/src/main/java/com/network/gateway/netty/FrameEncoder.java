package com.network.gateway.netty;

import com.network.common.protocol.Frame;
import com.network.common.protocol.FrameCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class FrameEncoder extends MessageToByteEncoder<Frame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Frame msg, ByteBuf out) {
        // pooled ByteBuf(out)에 직접 쓰기 — 별도 할당/해제 없음
        FrameCodec.encodeTo(msg, out);
    }
}
