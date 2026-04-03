package com.tunindex.market_tool.core.webscraping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class RateLimiterManager {

    @Value("${market-tool.scraping.rate-limit-delay:1500}")
    private long delayMs;

    private final AtomicLong lastCallTime = new AtomicLong(0);

    /**
     * Wait for rate limit slot - Reactive version returning Mono<Void>
     */
    public Mono<Void> waitForSlot() {
        return Mono.fromRunnable(this::waitForSlotSync);
    }

    /**
     * Synchronous wait for rate limit slot
     */
    public void waitForSlotSync() {
        long now = System.currentTimeMillis();
        long lastCall = lastCallTime.get();
        long elapsed = now - lastCall;

        if (elapsed < delayMs) {
            long waitTime = delayMs - elapsed;
            log.debug("Rate limiting: waiting {}ms before next request", waitTime);
            try {
                TimeUnit.MILLISECONDS.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Rate limit wait interrupted");
            }
        }

        lastCallTime.set(System.currentTimeMillis());
    }

    public boolean needsWait() {
        long now = System.currentTimeMillis();
        long lastCall = lastCallTime.get();
        long elapsed = now - lastCall;
        return elapsed < delayMs;
    }

    public long getRemainingWaitTime() {
        long now = System.currentTimeMillis();
        long lastCall = lastCallTime.get();
        long elapsed = now - lastCall;

        if (elapsed < delayMs) {
            return delayMs - elapsed;
        }
        return 0;
    }

    public void reset() {
        lastCallTime.set(0);
        log.debug("Rate limiter reset");
    }

    public void setDelay(long delayMs) {
        this.delayMs = delayMs;
        log.info("Rate limit delay updated to {}ms", delayMs);
    }

    public long getDelay() {
        return delayMs;
    }
}