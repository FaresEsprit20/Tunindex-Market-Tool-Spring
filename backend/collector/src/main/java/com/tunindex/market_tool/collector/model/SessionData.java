package com.tunindex.market_tool.collector.model;

import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Data
public class SessionData {
    private final String sessionId;
    private final Map<String, CookieData> cookies = new ConcurrentHashMap<>();
    private final Instant createdAt = Instant.now();
    private Instant lastAccessedAt = Instant.now();
    private int requestCount = 0;

    public SessionData(String sessionId) {
        this.sessionId = sessionId;
    }

    public void addCookie(String name, String value) {
        cookies.put(name, new CookieData(value, Instant.now().plusSeconds(3600)));
        lastAccessedAt = Instant.now();
        requestCount++;
    }

    public String getCookies() {
        cleanExpiredCookies();
        return cookies.entrySet().stream()
            .filter(entry -> entry.getValue().getExpiresAt().isAfter(Instant.now()))
            .map(entry -> entry.getKey() + "=" + entry.getValue().getValue())
            .collect(Collectors.joining("; "));
    }

    public int getCookieCount() {
        cleanExpiredCookies();
        return cookies.size();
    }

    private void cleanExpiredCookies() {
        Instant now = Instant.now();
        cookies.entrySet().removeIf(entry -> 
            entry.getValue().getExpiresAt().isBefore(now));
    }

    @Data
    public static class CookieData {
        private final String value;
        private final Instant expiresAt;

        public CookieData(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
