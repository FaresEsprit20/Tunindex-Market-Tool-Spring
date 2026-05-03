package com.tunindex.market_tool.api.exception;

public class SmsServiceException extends RuntimeException {

    public SmsServiceException(String message) {
        super(message);
    }

    public SmsServiceException(String message, Throwable cause) {
        super(message, cause);
    }

}
