package com.tunindex.market_tool.common.exception;

public class ScrapingException extends RuntimeException {
    
    public ScrapingException(String message) {
        super(message);
    }

    public ScrapingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScrapingException(Throwable cause) {
        super(cause);
    }
}
