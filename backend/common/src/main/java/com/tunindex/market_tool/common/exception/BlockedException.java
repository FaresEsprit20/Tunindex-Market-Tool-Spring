package com.tunindex.market_tool.common.exception;

public class BlockedException extends ScrapingException {
    private final int statusCode;
    private final long retryAfterSeconds;

    public BlockedException(int statusCode, String message) {
        this(statusCode, message, 60);
    }

    public BlockedException(int statusCode, String message, long retryAfterSeconds) {
        super(message);
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean isRateLimit() {
        return statusCode == 429 || statusCode == 503;
    }

    public boolean isCloudflare() {
        return statusCode == 503 || getMessage().contains("Cloudflare");
    }

    public boolean isCaptcha() {
        return statusCode == 403 || getMessage().contains("CAPTCHA");
    }

    public boolean isForbidden() {
        return statusCode == 403;
    }
}
