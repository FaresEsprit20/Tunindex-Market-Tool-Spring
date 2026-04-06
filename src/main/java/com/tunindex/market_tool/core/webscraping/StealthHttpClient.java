package com.tunindex.market_tool.core.webscraping;

import com.tunindex.market_tool.core.exception.CaptchaException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.time.Duration;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class StealthHttpClient {

    private final UserAgentManager userAgentManager;
    private final BrowserFingerprintGenerator fingerprintGenerator;
    private final ProxyManager proxyManager;
    private final RateLimiterManager rateLimiterManager;
    private final CaptchaDetector captchaDetector;
    private final RetryManager retryManager;

    public Mono<Object> fetchWithStealth(String url, boolean useProxy, String symbol) {
        log.debug("Fetching URL with stealth: {}", url);

        return Mono.fromCallable(() -> {
            // Apply rate limiting (matches Python rate_limiter.py)
            rateLimiterManager.waitForSlot();

            // Build WebClient with stealth headers
            WebClient client = buildStealthWebClient(useProxy);

            return client;
        }).flatMap(client ->
                client.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class)
                        .handle((response, sink) -> {
                            // Check for CAPTCHA
                            if (captchaDetector.hasCaptcha(response)) {
                                String captchaType = captchaDetector.getCaptchaType(response);
                                log.error("CAPTCHA detected for {}: {}", url, captchaType);
                                sink.error(new CaptchaException(
                                        ErrorCodes.CAPTCHA_DETECTED,
                                        Constants.PROVIDER_INVESTINGCOM,
                                        captchaType,
                                        "CAPTCHA detected when fetching URL",
                                        Collections.singletonList(url)
                                ));
                            } else if (captchaDetector.isBlocked(response)) {
                                log.error("Request blocked for URL: {}", url);
                                sink.error(new CaptchaException(
                                        ErrorCodes.BLOCKED_BY_PROVIDER,
                                        Constants.PROVIDER_INVESTINGCOM,
                                        "BLOCKED",
                                        "Request blocked by provider",
                                        Collections.singletonList(url)
                                ));
                            } else {
                                sink.next(response);
                            }
                        })
                        .retryWhen(retryManager.createRetrySpec())
                        .timeout(Duration.ofSeconds(Constants.REQUEST_TIMEOUT_MS / 1000))
        ).onErrorResume(e -> {
            if (e instanceof CaptchaException) {
                return Mono.error(e);
            }
            log.error("Stealth fetch failed for {}: {}", url, e.getMessage());
            return Mono.error(new RuntimeException("Stealth fetch failed: " + e.getMessage(), e));
        });
    }

    private WebClient buildStealthWebClient(boolean useProxy) {
        HttpClient httpClient = HttpClient.create();

        // Configure proxy if needed
        if (useProxy && proxyManager.hasProxies()) {
            String proxy = proxyManager.getRandomProxy();
            if (proxy != null) {
                String[] proxyParts = proxy.split(":");
                if (proxyParts.length >= 2) {
                    String host = proxyParts[0];
                    int port = Integer.parseInt(proxyParts[1]);

                    httpClient = httpClient.proxy(proxySpec -> proxySpec
                            .type(ProxyProvider.Proxy.HTTP)
                            .host(host)
                            .port(port));
                    log.debug("Using proxy: {}:{}", host, port);
                }
            }
        }

        // Build WebClient with stealth headers
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", userAgentManager.getRandomUserAgent())
                .defaultHeader("Accept", Constants.DEFAULT_ACCEPT)
                .defaultHeader("Accept-Language", fingerprintGenerator.getAcceptLanguage())
                .defaultHeader("Accept-Encoding", Constants.DEFAULT_ACCEPT_ENCODING)
                .defaultHeader("Connection", Constants.DEFAULT_CONNECTION)
                .defaultHeader("Upgrade-Insecure-Requests", "1")
                .defaultHeader("Cache-Control", "max-age=0")
                .defaultHeader("Sec-Ch-Ua", fingerprintGenerator.getSecChUa())
                .defaultHeader("Sec-Ch-Ua-Mobile", "?0")
                .defaultHeader("Sec-Ch-Ua-Platform", fingerprintGenerator.getPlatform())
                .defaultHeader("Sec-Fetch-Dest", "document")
                .defaultHeader("Sec-Fetch-Mode", "navigate")
                .defaultHeader("Sec-Fetch-Site", "none")
                .defaultHeader("Sec-Fetch-User", "?1")
                .build();
    }
}