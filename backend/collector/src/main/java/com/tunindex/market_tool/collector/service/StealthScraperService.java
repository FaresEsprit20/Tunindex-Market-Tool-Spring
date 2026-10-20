package com.tunindex.market_tool.collector.service;

import com.tunindex.market_tool.collector.model.BrowserFingerprint;
import com.tunindex.market_tool.collector.model.ScrapingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class StealthScraperService {

    private final WebClient webClient;
    private final ProxyRotationService proxyRotationService;

    public Mono<ScrapingResult> scrape(String url) {
        return scrapeWithRetry(url, 0);
    }

    private Mono<ScrapingResult> scrapeWithRetry(String url, int attempt) {
        Instant start = Instant.now();
        String proxyUsed = proxyRotationService.getCurrentProxy();
        BrowserFingerprint fingerprint = BrowserFingerprint.generate();

        log.info("🎯 Scraping attempt {}: {}", attempt + 1, url);
        log.debug("🔄 Using fingerprint: {}", fingerprint.getUserAgent());

        return webClient.get()
            .uri(url)
            .headers(headers -> {
                // Add fingerprint-specific headers
                headers.set("User-Agent", fingerprint.getUserAgent());
                headers.set("Accept-Language", fingerprint.getAcceptLanguage());
            })
            .retrieve()
            .bodyToMono(String.class)
            .map(content -> {
                Instant end = Instant.now();
                long responseTime = Duration.between(start, end).toMillis();
                
                log.info("✅ Scraping successful in {}ms", responseTime);
                
                return ScrapingResult.builder()
                    .url(url)
                    .content(content)
                    .statusCode(200)
                    .contentType("text/html")
                    .contentLength(content.length())
                    .responseTime(responseTime)
                    .timestamp(Instant.now())
                    .success(true)
                    .proxyUsed(proxyUsed)
                    .retryCount(attempt)
                    .fingerprint(fingerprint)
                    .headers(new HashMap<>())
                    .build();
            })
            .onErrorResume(throwable -> {
                log.warn("⚠️ Scraping failed for {}: {}", url, throwable.getMessage());
                
                if (attempt < 3) {
                    return handleErrorAndRetry(url, attempt, throwable);
                }
                
                log.error("❌ All retry attempts exhausted for: {}", url);
                return Mono.just(ScrapingResult.builder()
                    .url(url)
                    .success(false)
                    .errorMessage("All retry attempts exhausted: " + throwable.getMessage())
                    .timestamp(Instant.now())
                    .retryCount(attempt)
                    .build());
            });
    }

    private Mono<ScrapingResult> handleErrorAndRetry(String url, int attempt, Throwable error) {
        // Rotate proxy on failure
        proxyRotationService.rotateProxy();
        
        // Exponential backoff with jitter
        long delay = (long) Math.pow(2, attempt) * 1000;
        delay += (long) (Math.random() * 500);
        
        log.info("⏳ Retrying in {}ms (attempt {})", delay, attempt + 2);
        
        return Mono.delay(Duration.ofMillis(delay))
            .flatMap(ignored -> scrapeWithRetry(url, attempt + 1));
    }

    public Mono<ScrapingResult> scrapeWithFingerprint(String url, BrowserFingerprint fingerprint) {
        return webClient.get()
            .uri(url)
            .headers(headers -> {
                headers.set("User-Agent", fingerprint.getUserAgent());
                headers.set("Accept-Language", fingerprint.getAcceptLanguage());
                headers.set("Sec-Ch-Ua", fingerprint.getSecChUa());
                headers.set("Sec-Ch-Ua-Platform", fingerprint.getPlatform());
            })
            .retrieve()
            .bodyToMono(String.class)
            .map(content -> ScrapingResult.builder()
                .url(url)
                .content(content)
                .statusCode(200)
                .success(true)
                .fingerprint(fingerprint)
                .build())
            .onErrorResume(error -> {
                log.error("Scraping with custom fingerprint failed: {}", error.getMessage());
                return Mono.just(ScrapingResult.builder()
                    .url(url)
                    .success(false)
                    .errorMessage(error.getMessage())
                    .build());
            });
    }
}
