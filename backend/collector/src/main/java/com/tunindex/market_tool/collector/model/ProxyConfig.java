package com.tunindex.market_tool.collector.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProxyConfig {
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean active;
    private int successCount;
    private int failureCount;
    private long requestCount;

    public enum ProxyType {
        HTTP, HTTPS, SOCKS4, SOCKS5
    }

    public double getSuccessRate() {
        long total = successCount + failureCount;
        return total > 0 ? (double) successCount / total : 0.0;
    }

    public void incrementSuccess() {
        successCount++;
        requestCount++;
        active = true;
    }

    public void incrementFailure() {
        failureCount++;
        requestCount++;
        if (failureCount > 5) {
            active = false;
        }
    }

    public void resetStats() {
        successCount = 0;
        failureCount = 0;
        requestCount = 0;
        active = true;
    }
}
