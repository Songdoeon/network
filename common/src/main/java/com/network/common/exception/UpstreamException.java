package com.network.common.exception;

public class UpstreamException extends RuntimeException {

    private final String reasonCode;

    public UpstreamException(String message, String reasonCode) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public UpstreamException(String message, String reasonCode, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
