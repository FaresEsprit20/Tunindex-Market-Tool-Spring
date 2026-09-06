package com.tunindex.market_tool.common.filter;

import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class RequestThrottlingFilter implements ExchangeFilterFunction {

    private final RateLimiter rateLimiter;
    private final AtomicInteger concurrentRequests = new AtomicInteger(0);
    private final int maxConcurrentRequests;

    public RequestThrottlingFilter() {
        this(2.0, 5);
    }

    public RequestThrottlingFilter(double requestsPerSecond, int maxConcurrentRequests) {
        this.rateLimiter = RateLimiter.create(requestsPerSecond);
        this.maxConcurrentRequests = maxConcurrentRequests;
        log.info("🚦 RequestThrottlingFilter initialized: {} req/sec, max concurrent: {}", 
            requestsPerSecond, maxConcurrentRequests);
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.fromRunnable(() -> {
                double waitTime = rateLimiter.acquire();
                if (waitTime > 0.1) {
                    log.debug("⏳ Rate limiter wait: {}ms", waitTime * 1000);
                }
            })
            .then(Mono.defer(() -> {
                int concurrent = concurrentRequests.incrementAndGet();
                if (concurrent > maxConcurrentRequests) {
                    log.warn("⚠️ Max concurrent requests reached: {}/{}", concurrent, maxConcurrentRequests);
                } else {
                    log.debug("📊 Concurrent requests: {}/{}", concurrent, maxConcurrentRequests);
                }
                return next.exchange(request);
            }))
            .doFinally(signalType -> {
                int concurrent = concurrentRequests.decrementAndGet();
                log.debug("📊 Concurrent requests after: {}/{}", concurrent, maxConcurrentRequests);
            });
    }
}
