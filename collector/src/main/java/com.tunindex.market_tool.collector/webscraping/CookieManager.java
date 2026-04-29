package com.tunindex.market_tool.collector.webscraping;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CookieManager {

    private final Map<String, String> cookieStore = new ConcurrentHashMap<>();

    public void setCookie(String name, String value) {
        cookieStore.put(name, value);
    }

    public String getCookieHeader() {
        if (cookieStore.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        cookieStore.forEach((k, v) -> sb.append(k).append("=").append(v).append("; "));
        return sb.toString();
    }

    public void clear() {
        cookieStore.clear();
    }
}