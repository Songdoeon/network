package com.network.common.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

/**
 * Frame 인코딩/디코딩.
 * Wire format: [LEN(4)][CORR_ID(8)][MSG_TYPE(2)][BODY(...)]
 * LEN = CORR_ID(8) + MSG_TYPE(2) + BODY 길이 (LEN 필드 자체는 미포함)
 */
public final class FrameCodec {

    private FrameCodec() {
    }

    public static ByteBuf encode(Frame frame) {
        byte[] corrIdBytes = frame.correlationId().getBytes(StandardCharsets.US_ASCII);
        if (corrIdBytes.length != 8) {
            throw new IllegalArgumentException(
                    "correlationId must be exactly 8 ASCII bytes, got: " + corrIdBytes.length);
        }

        byte[] body = frame.body() != null ? frame.body() : new byte[0];
        int payloadLength = 8 + 2 + body.length;

        ByteBuf buf = Unpooled.buffer(4 + payloadLength);
        buf.writeInt(payloadLength);
        buf.writeBytes(corrIdBytes);
        buf.writeShort(frame.messageType().code());
        buf.writeBytes(body);
        return buf;
    }

    public static Frame decode(ByteBuf buf) {
        int length = buf.readInt();

        byte[] corrIdBytes = new byte[8];
        buf.readBytes(corrIdBytes);
        String correlationId = new String(corrIdBytes, StandardCharsets.US_ASCII);

        short msgTypeCode = buf.readShort();
        MessageType messageType = MessageType.fromCode(msgTypeCode);

        int bodyLength = length - 8 - 2;
        byte[] body;
        if (bodyLength > 0) {
            body = new byte[bodyLength];
            buf.readBytes(body);
        } else {
            body = new byte[0];
        }

        return new Frame(correlationId, messageType, body);
    }
}
