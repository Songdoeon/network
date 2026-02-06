package com.network.cardsim.server;

import com.network.cardsim.scenario.DisconnectInjector;
import com.network.cardsim.scenario.ErrorInjector;
import com.network.cardsim.scenario.LatencyInjector;
import com.network.cardsim.scenario.OutOfOrderInjector;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;

@RequiredArgsConstructor
@Component
public class CardSimChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final LatencyInjector latencyInjector;
    private final ErrorInjector errorInjector;
    private final OutOfOrderInjector outOfOrderInjector;
    private final DisconnectInjector disconnectInjector;
    private final ScheduledExecutorService scheduler;

    @Override
    protected void initChannel(SocketChannel ch) {
        disconnectInjector.getChannels().add(ch);

        ch.pipeline()
                .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                        1024 * 1024, 0, 4, 0, 0))
                .addLast("handler", new CardSimHandler(latencyInjector, errorInjector, outOfOrderInjector, scheduler));
    }
}
