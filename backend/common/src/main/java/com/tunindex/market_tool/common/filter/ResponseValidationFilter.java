package com.tunindex.market_tool.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

@Slf4j
public class ResponseValidationFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(request)
            .flatMap(response -> {
                int status = response.statusCode().value();
                
                if (status >= 200 && status < 300) {
                    log.debug("✅ Success response: {} for {}", status, request.url());
                    return validateResponseBody(request, response);
                }
                
                if (status == 403) {
                    log.warn("⛔ Forbidden access to: {}", request.url());
                    return checkForBlockingHeaders(request, response);
                }
                
                if (status == 429) {
                    log.warn("🚫 Rate limited: {}", request.url());
                    return handleRateLimit(request, response);
                }
                
                if (status >= 500) {
                    log.warn("🔴 Server error {} for: {}", status, request.url());
                    return Mono.error(new RuntimeException("Server error: " + status));
                }
                
                log.debug("📊 Response status: {} for {}", status, request.url());
                return Mono.just(response);
            });
    }

    private Mono<ClientResponse> validateResponseBody(ClientRequest request, ClientResponse response) {
        return response.bodyToMono(String.class)
            .flatMap(body -> {
                // Check for hidden blockers
                if (body.contains("Access Denied") || 
                    body.contains("Your IP has been blocked") ||
                    body.contains("Please verify you are a human")) {
                    log.warn("🛡️ Hidden block detected for: {}", request.url());
                    return Mono.error(new RuntimeException("Hidden block detected"));
                }
                
                // Check for Cloudflare challenge
                if (body.contains("Cloudflare") || 
                    body.contains("cf-browser-verification") ||
                    body.contains("data-cf-beacon")) {
                    log.warn("☁️ Cloudflare challenge detected for: {}", request.url());
                    return Mono.error(new RuntimeException("Cloudflare challenge"));
                }
                
                // Check for CAPTCHA
                if (body.contains("captcha") || 
                    body.contains("recaptcha") ||
                    body.contains("h-captcha")) {
                    log.warn("🔐 CAPTCHA detected for: {}", request.url());
                    return Mono.error(new RuntimeException("CAPTCHA required"));
                }
                
                // Check for empty response
                if (body.trim().isEmpty()) {
                    log.warn("📭 Empty response body for: {}", request.url());
                    return Mono.error(new RuntimeException("Empty response"));
                }
                
                log.debug("✅ Response validation passed for: {}", request.url());
                return Mono.just(response);
            })
            .switchIfEmpty(Mono.error(new RuntimeException("Empty response body")));
    }

    private Mono<ClientResponse> checkForBlockingHeaders(ClientRequest request, ClientResponse response) {
        String server = response.headers().header("Server")
            .stream().findFirst().orElse("");
        String cfRay = response.headers().header("CF-RAY")
            .stream().findFirst().orElse("");
        
        if (server.toLowerCase().contains("cloudflare") || !cfRay.isEmpty()) {
            log.warn("☁️ Cloudflare detected via headers for: {}", request.url());
            return Mono.error(new RuntimeException("Cloudflare detected"));
        }
        
        return Mono.error(new RuntimeException("Access forbidden"));
    }

    private Mono<ClientResponse> handleRateLimit(ClientRequest request, ClientResponse response) {
        String retryAfter = response.headers().header("Retry-After")
            .stream().findFirst().orElse("60");
        log.warn("⏰ Rate limit. Retry after: {}s", retryAfter);
        return Mono.error(new RuntimeException("Rate limited: " + retryAfter));
    }
}
