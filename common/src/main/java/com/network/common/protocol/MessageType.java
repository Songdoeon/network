package com.network.common.protocol;

public enum MessageType {

    AUTH_REQ((short) 0x01),
    AUTH_RES((short) 0x02),
    CANCEL_REQ((short) 0x03),
    CANCEL_RES((short) 0x04),
    INQUIRY_REQ((short) 0x05),
    INQUIRY_RES((short) 0x06);

    private final short code;

    MessageType(short code) {
        this.code = code;
    }

    public short code() {
        return code;
    }

    public static MessageType fromCode(short code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type code: " + code);
    }
}
