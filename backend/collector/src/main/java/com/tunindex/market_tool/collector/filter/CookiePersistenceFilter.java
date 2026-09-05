package com.tunindex.market_tool.collector.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CookiePersistenceFilter implements ExchangeFilterFunction {

    private final Map<String, String> sessionCookies = new ConcurrentHashMap<>();
    private final Map<String, String> globalCookies = new ConcurrentHashMap<>();

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        String sessionId = extractSessionId(request);
        
        ClientRequest modifiedRequest = ClientRequest.from(request)
            .headers(headers -> {
                String cookieHeader = buildCookieString(sessionId);
                if (!cookieHeader.isEmpty()) {
                    headers.add(HttpHeaders.COOKIE, cookieHeader);
                    log.debug("🍪 Added cookies for session: {}", sessionId);
                }
            })
            .build();

        return next.exchange(modifiedRequest)
            .doOnNext(response -> {
                response.cookies().forEach((name, values) -> {
                    values.forEach(value -> {
                        sessionCookies.put(name, value.getValue());
                        globalCookies.put(name, value.getValue());
                        log.debug("🍪 Stored cookie: {}={}", name, value.getValue());
                    });
                });
            });
    }

    private String extractSessionId(ClientRequest request) {
        String host = request.url().getHost();
        String path = request.url().getPath();
        return host + (path != null ? path.substring(0, Math.min(path.length(), 30)) : "");
    }

    private String buildCookieString(String sessionId) {
        StringBuilder cookieBuilder = new StringBuilder();
        
        // Add session cookies
        sessionCookies.forEach((key, value) -> {
            if (cookieBuilder.length() > 0) cookieBuilder.append("; ");
            cookieBuilder.append(key).append("=").append(value);
        });
        
        // Add global cookies not already in session
        globalCookies.forEach((key, value) -> {
            if (!sessionCookies.containsKey(key)) {
                if (cookieBuilder.length() > 0) cookieBuilder.append("; ");
                cookieBuilder.append(key).append("=").append(value);
            }
        });
        
        return cookieBuilder.toString();
    }

    public void clearCookies() {
        sessionCookies.clear();
        globalCookies.clear();
        log.info("🧹 Cleared all cookies");
    }
}
