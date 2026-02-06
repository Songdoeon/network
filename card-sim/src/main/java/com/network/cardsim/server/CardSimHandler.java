package com.network.cardsim.server;

import com.network.cardsim.scenario.ErrorInjector;
import com.network.cardsim.scenario.LatencyInjector;
import com.network.cardsim.scenario.OutOfOrderInjector;
import com.network.common.dto.TransactionStatus;
import com.network.common.protocol.Frame;
import com.network.common.protocol.FrameCodec;
import com.network.common.protocol.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class CardSimHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final LatencyInjector latencyInjector;
    private final ErrorInjector errorInjector;
    private final OutOfOrderInjector outOfOrderInjector;
    private final ScheduledExecutorService scheduler;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        Frame requestFrame = FrameCodec.decode(msg);
        log.debug("Received request: correlationId={}, type={}", requestFrame.correlationId(), requestFrame.messageType());

        long delay = latencyInjector.calculateDelay();

        if (outOfOrderInjector.shouldReorder()) {
            delay += outOfOrderInjector.reorderDelay();
            log.debug("Out-of-order injection: correlationId={}, extra delay={}ms", requestFrame.correlationId(), delay);
        }

        scheduler.schedule(() -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            Frame response = buildResponse(requestFrame);
            ByteBuf encoded = FrameCodec.encode(response);
            ctx.writeAndFlush(encoded);
            log.debug("Sent response: correlationId={}, status={}", response.correlationId(),
                    new String(response.body(), StandardCharsets.UTF_8).split("\\|")[0]);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private Frame buildResponse(Frame request) {
        String correlationId = request.correlationId();
        MessageType responseType = getResponseType(request.messageType());

        String status;
        String reasonCode;

        if (errorInjector.shouldInjectError()) {
            status = TransactionStatus.DECLINED.name();
            reasonCode = "INSUFFICIENT_FUNDS";
        } else {
            status = TransactionStatus.APPROVED.name();
            reasonCode = "OK";
        }

        byte[] responseBody = (status + "|" + reasonCode).getBytes(StandardCharsets.UTF_8);
        return new Frame(correlationId, responseType, responseBody);
    }

    private MessageType getResponseType(MessageType requestType) {
        return switch (requestType) {
            case AUTH_REQ -> MessageType.AUTH_RES;
            case CANCEL_REQ -> MessageType.CANCEL_RES;
            case INQUIRY_REQ -> MessageType.INQUIRY_RES;
            default -> MessageType.AUTH_RES;
        };
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("CardSim handler exception: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
