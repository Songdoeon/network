package com.network.gateway.netty;

import com.network.gateway.mux.MuxEngine;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GatewayChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final MuxEngine muxEngine;

    public GatewayChannelInitializer(@Lazy MuxEngine muxEngine) {
        this.muxEngine = muxEngine;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast("frameDecoder", new FrameDecoder())
                .addLast("responseHandler", new ResponseHandler(muxEngine));
    }
}
