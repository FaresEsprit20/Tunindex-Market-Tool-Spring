package com.tunindex.market_tool.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class FingerprintRotationFilter implements ExchangeFilterFunction {

    private final AtomicInteger requestCount = new AtomicInteger(0);
    private String currentUserAgent;
    private String currentAcceptLanguage;
    private String currentPlatform;

    private final List<String> userAgents = Arrays.asList(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    );

    private final List<String> acceptLanguages = Arrays.asList(
        "en-US,en;q=0.9,fr;q=0.8,de;q=0.7",
        "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7",
        "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7",
        "es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7"
    );

    private final List<String> platforms = Arrays.asList("\"Windows\"", "\"macOS\"", "\"Linux\"");
    private final List<String> platformVersions = Arrays.asList("\"15.0.0\"", "\"14.0.0\"", "\"13.0.0\"");
    private final List<String> arches = Arrays.asList("\"x64\"", "\"x86\"", "\"arm64\"");

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        int count = requestCount.incrementAndGet();
        
        // Initialize with random values on first request
        if (currentUserAgent == null) {
            rotateFingerprint();
        }
        
        // Rotate fingerprint every 5 requests
        if (count % 5 == 0) {
            rotateFingerprint();
            log.info("🔄 Fingerprint rotated (request {})", count);
        }
        
        // Apply current fingerprint
        ClientRequest modifiedRequest = ClientRequest.from(request)
            .headers(headers -> {
                headers.set("User-Agent", currentUserAgent);
                headers.set("Accept-Language", currentAcceptLanguage);
                headers.set("Sec-Ch-Ua-Platform", currentPlatform);
                headers.set("Sec-Ch-Ua-Platform-Version", 
                    platformVersions.get(ThreadLocalRandom.current().nextInt(platformVersions.size())));
                headers.set("Sec-Ch-Ua-Arch", 
                    arches.get(ThreadLocalRandom.current().nextInt(arches.size())));
                headers.set("Sec-Ch-Ua", 
                    "\"Google Chrome\";v=\"130\", \"Chromium\";v=\"130\", \"Not_A Brand\";v=\"99\"");
            })
            .build();
        
        return next.exchange(modifiedRequest);
    }

    private void rotateFingerprint() {
        currentUserAgent = userAgents.get(ThreadLocalRandom.current().nextInt(userAgents.size()));
        currentAcceptLanguage = acceptLanguages.get(ThreadLocalRandom.current().nextInt(acceptLanguages.size()));
        currentPlatform = platforms.get(ThreadLocalRandom.current().nextInt(platforms.size()));
        
        log.debug("🔄 New fingerprint: User-Agent: {}, Platform: {}", 
            currentUserAgent.substring(0, Math.min(50, currentUserAgent.length())) + "...", 
            currentPlatform);
    }

    public void forceRotation() {
        rotateFingerprint();
        log.info("🔄 Forced fingerprint rotation");
    }
}
