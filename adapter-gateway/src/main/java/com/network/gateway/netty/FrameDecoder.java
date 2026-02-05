package com.network.gateway.netty;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * LengthFieldBasedFrameDecoder 래퍼.
 * 프레임: [LEN(4)][CORR_ID(8)][MSG_TYPE(2)][BODY(...)]
 * LEN 필드는 4바이트, LEN 이후의 바이트 수를 나타냄.
 */
public class FrameDecoder extends LengthFieldBasedFrameDecoder {

    private static final int MAX_FRAME_LENGTH = 1024 * 1024; // 1MB
    private static final int LENGTH_FIELD_OFFSET = 0;
    private static final int LENGTH_FIELD_LENGTH = 4;
    private static final int LENGTH_ADJUSTMENT = 0;
    private static final int INITIAL_BYTES_TO_STRIP = 0; // LEN 필드도 포함해서 전달 (FrameCodec.decode에서 읽음)

    public FrameDecoder() {
        super(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
    }
}
