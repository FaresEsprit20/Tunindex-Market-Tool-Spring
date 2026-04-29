package com.tunindex.market_tool.core.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class EnrichmentException extends InvalidOperationException {

    private final String stockSymbol;
    private final String metricName;

    public EnrichmentException(String stockSymbol, String metricName, String message) {
        super(message, ErrorCodes.ENRICHMENT_ERROR, null);
        this.stockSymbol = stockSymbol;
        this.metricName = metricName;
    }

    public EnrichmentException(ErrorCodes errorCode, String stockSymbol, String metricName, String message, List<String> errors) {
        super(message, errorCode, errors);
        this.stockSymbol = stockSymbol;
        this.metricName = metricName;
    }

    public EnrichmentException(String stockSymbol, String metricName, String message, Throwable cause, List<String> errors) {
        super(message, cause, ErrorCodes.ENRICHMENT_ERROR, errors);
        this.stockSymbol = stockSymbol;
        this.metricName = metricName;
    }
}