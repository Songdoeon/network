package com.network.common.protocol;

import io.netty.buffer.ByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameCodecTest {

    @Test
    void encodeAndDecode_roundTrip() {
        Frame original = new Frame("00000001", MessageType.AUTH_REQ, "hello".getBytes(StandardCharsets.UTF_8));

        ByteBuf encoded = FrameCodec.encode(original);
        Frame decoded = FrameCodec.decode(encoded);

        assertThat(decoded.correlationId()).isEqualTo("00000001");
        assertThat(decoded.messageType()).isEqualTo(MessageType.AUTH_REQ);
        assertThat(new String(decoded.body(), StandardCharsets.UTF_8)).isEqualTo("hello");

        encoded.release();
    }

    @Test
    void encodeAndDecode_emptyBody() {
        Frame original = new Frame("00000002", MessageType.CANCEL_RES, new byte[0]);

        ByteBuf encoded = FrameCodec.encode(original);
        Frame decoded = FrameCodec.decode(encoded);

        assertThat(decoded.correlationId()).isEqualTo("00000002");
        assertThat(decoded.messageType()).isEqualTo(MessageType.CANCEL_RES);
        assertThat(decoded.body()).isEmpty();

        encoded.release();
    }

    @Test
    void encodeAndDecode_nullBody() {
        Frame original = new Frame("00000003", MessageType.INQUIRY_REQ, null);

        ByteBuf encoded = FrameCodec.encode(original);
        Frame decoded = FrameCodec.decode(encoded);

        assertThat(decoded.correlationId()).isEqualTo("00000003");
        assertThat(decoded.body()).isEmpty();

        encoded.release();
    }

    @Test
    void encode_lengthField_isCorrect() {
        byte[] body = "test-body".getBytes(StandardCharsets.UTF_8);
        Frame frame = new Frame("00000004", MessageType.AUTH_RES, body);

        ByteBuf encoded = FrameCodec.encode(frame);

        int lengthField = encoded.readInt();
        assertThat(lengthField).isEqualTo(8 + 2 + body.length);

        encoded.release();
    }

    @Test
    void encode_invalidCorrelationIdLength_throws() {
        Frame frame = new Frame("short", MessageType.AUTH_REQ, new byte[0]);

        assertThatThrownBy(() -> FrameCodec.encode(frame))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8 ASCII bytes");
    }

    @Test
    void encodeAndDecode_allMessageTypes() {
        for (MessageType type : MessageType.values()) {
            Frame original = new Frame("00000099", type, "data".getBytes(StandardCharsets.UTF_8));
            ByteBuf encoded = FrameCodec.encode(original);
            Frame decoded = FrameCodec.decode(encoded);

            assertThat(decoded.messageType()).isEqualTo(type);
            encoded.release();
        }
    }

    @Test
    void encodeAndDecode_multipleFramesSequential() {
        Frame frame1 = new Frame("00000010", MessageType.AUTH_REQ, "req1".getBytes(StandardCharsets.UTF_8));
        Frame frame2 = new Frame("00000011", MessageType.AUTH_RES, "res1".getBytes(StandardCharsets.UTF_8));

        ByteBuf buf1 = FrameCodec.encode(frame1);
        ByteBuf buf2 = FrameCodec.encode(frame2);

        Frame decoded1 = FrameCodec.decode(buf1);
        Frame decoded2 = FrameCodec.decode(buf2);

        assertThat(decoded1.correlationId()).isEqualTo("00000010");
        assertThat(decoded2.correlationId()).isEqualTo("00000011");

        buf1.release();
        buf2.release();
    }
}
