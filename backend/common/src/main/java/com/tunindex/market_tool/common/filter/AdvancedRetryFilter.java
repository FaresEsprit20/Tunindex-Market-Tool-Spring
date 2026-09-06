package com.tunindex.market_tool.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class AdvancedRetryFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(request)
            .flatMap(response -> {
                int status = response.statusCode().value();
                
                // Handle rate limiting
                if (status == 429) {
                    String retryAfter = response.headers().header("Retry-After")
                        .stream().findFirst().orElse("5");
                    log.warn("🚫 Rate limited. Retry after: {}s", retryAfter);
                    return Mono.error(new RuntimeException("Rate limited: " + retryAfter));
                }
                
                // Handle server errors
                if (status >= 500) {
                    log.warn("🔴 Server error: {}", status);
                    return Mono.error(new RuntimeException("Server error: " + status));
                }
                
                // Handle forbidden (possible block)
                if (status == 403) {
                    log.warn("⛔ Access forbidden: {}", request.url());
                    return Mono.error(new RuntimeException("Access forbidden"));
                }
                
                return Mono.just(response);
            })
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                .maxBackoff(Duration.ofSeconds(30))
                .jitter(0.5)
                .doBeforeRetry(retrySpec -> {
                    log.warn("🔄 Retry attempt {} after failure", retrySpec.totalRetries());
                    try {
                        // Add jitter to retry delay
                        long delay = (long) Math.pow(2, retrySpec.totalRetries()) * 1000;
                        delay += ThreadLocalRandom.current().nextInt(500);
                        Thread.sleep(Math.min(delay, 30000));
                    } catch (InterruptedException ignored) {}
                })
            )
            .onErrorResume(error -> {
                log.error("💥 All retries failed for: {}", request.url());
                return Mono.error(error);
            });
    }
}
