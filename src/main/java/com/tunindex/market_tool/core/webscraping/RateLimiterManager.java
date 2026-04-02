package com.tunindex.market_tool.core.webscraping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class RateLimiterManager {

    @Value("${market-tool.scraping.rate-limit-delay:1500}")
    private long delayMs;

    private final AtomicLong lastCallTime = new AtomicLong(0);

    /**
     * Wait for rate limit slot
     * Maps to Python: rate_limiter.py - wait()
     *
     * Python equivalent:
     * LAST_CALL = 0
     * DELAY = 1.5
     *
     * def wait():
     *     global LAST_CALL
     *     now = time.time()
     *     if now - LAST_CALL < DELAY:
     *         time.sleep(DELAY - (now - LAST_CALL))
     *     LAST_CALL = time.time()
     */
    public void waitForSlot() {
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

    /**
     * Check if we need to wait without actually waiting
     */
    public boolean needsWait() {
        long now = System.currentTimeMillis();
        long lastCall = lastCallTime.get();
        long elapsed = now - lastCall;
        return elapsed < delayMs;
    }

    /**
     * Get remaining wait time in milliseconds
     */
    public long getRemainingWaitTime() {
        long now = System.currentTimeMillis();
        long lastCall = lastCallTime.get();
        long elapsed = now - lastCall;

        if (elapsed < delayMs) {
            return delayMs - elapsed;
        }
        return 0;
    }

    /**
     * Reset the rate limiter (useful for testing)
     */
    public void reset() {
        lastCallTime.set(0);
        log.debug("Rate limiter reset");
    }

    /**
     * Update delay dynamically
     */
    public void setDelay(long delayMs) {
        this.delayMs = delayMs;
        log.info("Rate limit delay updated to {}ms", delayMs);
    }

    /**
     * Get current delay in milliseconds
     */
    public long getDelay() {
        return delayMs;
    }
}