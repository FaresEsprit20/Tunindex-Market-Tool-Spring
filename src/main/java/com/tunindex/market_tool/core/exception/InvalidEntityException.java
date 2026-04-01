package com.tunindex.market_tool.core.exception;

import java.util.List;

public class InvalidEntityException extends RuntimeException {

    private final ErrorCodes errorCode;
    private final List<String> errors;

    public InvalidEntityException(String message, ErrorCodes errorCode, List<String> errors) {
        super(message);
        this.errorCode = errorCode;
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }

    public ErrorCodes getErrorCode() {
        return errorCode;
    }



}

