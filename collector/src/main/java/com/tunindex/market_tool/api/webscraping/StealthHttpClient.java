package com.tunindex.market_tool.api.webscraping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class StealthHttpClient {

    private final RateLimiterManager rateLimiterManager;
    private final RetryManager retryManager;
    private final CurlHttpClient curlHttpClient;  // Add this

    public Mono<String> fetchWithStealth(String url, boolean useProxy, String symbol) {
        log.debug("Fetching URL with stealth: {}", url);

        return rateLimiterManager.waitForSlot()
                .then(Mono.fromCallable(() -> curlHttpClient.fetch(url)))
                .retryWhen(retryManager.createRetrySpec())
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    log.error("Stealth fetch failed for {}: {}", url, e.getMessage());
                    return Mono.empty();
                });
    }
}