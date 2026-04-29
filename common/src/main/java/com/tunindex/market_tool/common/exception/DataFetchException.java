package com.tunindex.market_tool.common.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class DataFetchException extends InvalidOperationException {

    private final String providerName;
    private final String symbol;

    public DataFetchException(String symbol, String message) {
        super(message, (List<String>) null);
        this.providerName = null;
        this.symbol = symbol;
    }

    public DataFetchException(ErrorCodes errorCode, String providerName, String symbol, String message, List<String> errors) {
        super(message, errorCode, errors);
        this.providerName = providerName;
        this.symbol = symbol;
    }
}