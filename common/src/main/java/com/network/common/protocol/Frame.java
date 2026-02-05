package com.network.common.protocol;

/**
 * TCP 전문통신 프레임.
 * Wire format: [LEN(4)][CORR_ID(8)][MSG_TYPE(2)][BODY(...)]
 * LEN = CORR_ID(8) + MSG_TYPE(2) + BODY 길이
 */
public record Frame(
        String correlationId,
        MessageType messageType,
        byte[] body
) {

    public int payloadLength() {
        return 8 + 2 + (body != null ? body.length : 0);
    }
}
