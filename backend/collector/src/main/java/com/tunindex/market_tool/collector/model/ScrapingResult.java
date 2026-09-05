package com.tunindex.market_tool.collector.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class ScrapingResult {
    private String url;
    private String content;
    private int statusCode;
    private String contentType;
    private long contentLength;
    private long responseTime;
    private Instant timestamp;
    private Map<String, String> headers;
    private boolean success;
    private String errorMessage;
    private String proxyUsed;
    private int retryCount;
    private BrowserFingerprint fingerprint;
}
