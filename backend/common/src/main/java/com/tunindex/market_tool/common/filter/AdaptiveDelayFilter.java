package com.tunindex.market_tool.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class AdaptiveDelayFilter implements ExchangeFilterFunction {

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long delay = calculateAdaptiveDelay();
        
        log.debug("⏱️ Adaptive delay: {}ms for {}", delay, request.url());
        
        return Mono.delay(Duration.ofMillis(delay))
            .then(next.exchange(request))
            .doOnError(error -> {
                consecutiveFailures.incrementAndGet();
                log.warn("⚠️ Request failed, consecutive failures: {}", consecutiveFailures.get());
            })
            .doOnSuccess(response -> {
                if (response.statusCode().is2xxSuccessful()) {
                    consecutiveFailures.set(0);
                    requestCount.incrementAndGet();
                } else {
                    consecutiveFailures.incrementAndGet();
                }
            });
    }

    private long calculateAdaptiveDelay() {
        int failures = consecutiveFailures.get();
        int requests = requestCount.get();
        
        // Base delay with jitter (100-500ms)
        long baseDelay = 100 + ThreadLocalRandom.current().nextInt(0, 400);
        
        // Increase delay on failures (exponential backoff)
        if (failures > 0) {
            baseDelay = (long) Math.pow(2, failures) * 1000;
        }
        
        // Randomize based on request count to prevent pattern detection
        if (requests % 10 == 0) {
            baseDelay += 500 + ThreadLocalRandom.current().nextInt(1000);
        }
        
        // Cap maximum delay at 30 seconds
        return Math.min(baseDelay, 30000);
    }
}
