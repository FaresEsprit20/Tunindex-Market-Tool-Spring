package com.tunindex.market_tool.core.webscraping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
@Slf4j
public class RetryManager {

    @Value("${market-tool.retry.max-attempts:3}")
    private int defaultMaxRetries;

    @Value("${market-tool.retry.initial-delay-ms:2000}")
    private long defaultDelayMs;

    @Value("${market-tool.retry.multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * Create retry specification for Reactor
     * Maps to Python: retry_manager.py - retry(max_retries=3, delay=2, backoff=2)
     *
     * Python equivalent:
     * def retry(max_retries=3, delay=2, backoff=2):
     *     def decorator(func):
     *         def wrapper(*args, **kwargs):
     *             current_delay = delay
     *             for i in range(max_retries):
     *                 try:
     *                     return func(*args, **kwargs)
     *                 except Exception as e:
     *                     print(f"Retry {i+1}/{max_retries} failed: {e}")
     *                     time.sleep(current_delay)
     *                     current_delay *= backoff
     *             return None
     *         return wrapper
     *     return decorator
     */
    public Retry createRetrySpec() {
        return createRetrySpec(defaultMaxRetries, defaultDelayMs, backoffMultiplier);
    }

    public Retry createRetrySpec(int maxRetries, long initialDelayMs, double multiplier) {
        return Retry.backoff(maxRetries, Duration.ofMillis(initialDelayMs))
                .maxBackoff(Duration.ofMillis((long) (initialDelayMs * Math.pow(multiplier, maxRetries - 1))))
                .doBeforeRetry(retrySignal -> {
                    long currentDelay = (long) (initialDelayMs * Math.pow(multiplier, retrySignal.totalRetries()));
                    log.warn("Retry {}/{} failed, waiting {}ms before next attempt",
                            retrySignal.totalRetries() + 1,
                            maxRetries,
                            currentDelay);
                })
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                    log.error("All {} retry attempts exhausted", maxRetries);
                    return retrySignal.failure();
                });
    }

    /**
     * Execute operation with retry (synchronous version)
     * Similar to Python decorator pattern
     */
    public <T> T executeWithRetry(RetryableOperation<T> operation) throws Exception {
        return executeWithRetry(operation, defaultMaxRetries, defaultDelayMs, backoffMultiplier);
    }

    public <T> T executeWithRetry(RetryableOperation<T> operation, int maxRetries, long initialDelayMs, double multiplier) throws Exception {
        long currentDelay = initialDelayMs;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Attempt {}/{}", attempt, maxRetries);
                return operation.execute();
            } catch (Exception e) {
                lastException = e;
                log.warn("Retry {}/{} failed: {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    log.debug("Waiting {}ms before next attempt", currentDelay);
                    Thread.sleep(currentDelay);
                    currentDelay = (long) (currentDelay * multiplier);
                }
            }
        }

        log.error("All {} retry attempts exhausted", maxRetries);
        throw lastException;
    }

    /**
     * Execute operation with retry and return Mono (reactive version)
     */
    public <T> Mono<T> executeWithRetryMono(Mono<T> mono) {
        return executeWithRetryMono(mono, defaultMaxRetries, defaultDelayMs);
    }

    public <T> Mono<T> executeWithRetryMono(Mono<T> mono, int maxRetries, long initialDelayMs) {
        return mono.retryWhen(
                Retry.backoff(maxRetries, Duration.ofMillis(initialDelayMs))
                        .maxBackoff(Duration.ofMillis(initialDelayMs * (long) Math.pow(backoffMultiplier, maxRetries - 1)))
                        .doBeforeRetry(retrySignal -> {
                            long currentDelay = (long) (initialDelayMs * Math.pow(backoffMultiplier, retrySignal.totalRetries()));
                            log.warn("Retry {}/{} failed, waiting {}ms",
                                    retrySignal.totalRetries() + 1,
                                    maxRetries,
                                    currentDelay);
                        })
        );
    }

    /**
     * Functional interface for retryable operations
     */
    @FunctionalInterface
    public interface RetryableOperation<T> {
        T execute() throws Exception;
    }
}