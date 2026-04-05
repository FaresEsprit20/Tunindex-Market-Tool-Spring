package com.tunindex.market_tool.core.config.flaresolverclient;

import com.tunindex.market_tool.core.utils.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class FlareSolverClient {

    private final WebClient webClient;

    public FlareSolverClient() {
        this.webClient = WebClient.builder()
                .baseUrl(Constants.FLARESOLVERR_URL)
                .build();
    }

    public Mono<String> fetch(String url) {
        return fetch(url, null);
    }

    public Mono<String> fetch(String url, String proxyUrl) {
        log.debug("Fetching URL via FlareSolverr: {}", url);

        Map<String, Object> request = new HashMap<>();
        request.put("cmd", "request.get");
        request.put("url", url);
        request.put("maxTimeout", Constants.FLARESOLVERR_TIMEOUT);

        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            Map<String, String> proxy = new HashMap<>();
            proxy.put("url", proxyUrl);
            request.put("proxy", proxy);
            log.debug("Using proxy: {}", proxyUrl);
        }

        return webClient.post()
                .uri("/v1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(Constants.FLARESOLVERR_TIMEOUT))
                .map(response -> extractHtmlFromResponse(response, url))
                .retry(Constants.FLARESOLVERR_MAX_RETRIES)
                .doOnSuccess(html -> {
                    if (html != null) {
                        log.debug("Successfully fetched {} (length: {})", url, html.length());
                    }
                })
                .onErrorResume(e -> {
                    log.error("FlareSolverr request failed for {}: {}", url, e.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unchecked")
    private String extractHtmlFromResponse(Map<String, Object> response, String url) {
        if (response == null) {
            log.warn("Empty response from FlareSolverr for {}", url);
            return null;
        }

        if (response.containsKey("solution")) {
            Map<String, Object> solution = (Map<String, Object>) response.get("solution");
            if (solution.containsKey("response")) {
                return (String) solution.get("response");
            } else if (solution.containsKey("html")) {
                return (String) solution.get("html");
            }
        }

        if (response.containsKey("status") && "error".equals(response.get("status"))) {
            String message = (String) response.get("message");
            log.error("FlareSolverr error for {}: {}", url, message);
        }

        log.warn("Unexpected response format from FlareSolverr for {}: {}", url, response);
        return null;
    }
}