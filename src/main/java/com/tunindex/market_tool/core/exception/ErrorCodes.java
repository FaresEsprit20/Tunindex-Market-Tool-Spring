package com.tunindex.market_tool.core.exception;

import lombok.Getter;

@Getter
public enum ErrorCodes {

    GENERIC_ERROR(50),
    RATE_LIMIT_EXCEEDED(100),
    INVALID_RESET_TOKEN(200),

    USER_NOT_FOUND(12000),
    USER_NOT_VALID(12001),
    USER_ALREADY_EXISTS(12002),
    USER_CHANGE_PASSWORD_OBJECT_NOT_VALID(12003),
    USER_ACCOUNT_LOCK_NOT_VALID(12004),
    USER_NOT_AUTHENTICATED(12005),

    BAD_CREDENTIALS(14003),

    UNKNOWN_CONTEXT(14001),

    DATABASE_ERROR(15000),

    PAGE_NOT_VALID(18000),
    SIZE_NOT_VALID(18001);


    private final int code;

    ErrorCodes(int code) {
        this.code = code;
    }

}
