package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.common.filter.AdaptiveDelayFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the backoff against the deadlock it used to cause.
 *
 * <p>The delay is applied before the request is sent, so if it can ever exceed
 * the caller's timeout, requests stop being attempted at all — and since a
 * timeout counts as another failure, the backoff grows further and nothing can
 * ever succeed again. That is not a hypothetical: it is what made the scraper
 * look like it had been blocked when the sites were answering normally.
 */
@DisplayName("AdaptiveDelayFilter backoff")
class AdaptiveDelayFilterTest {

    /** The shortest timeout any caller in this codebase uses. */
    private static final long SHORTEST_CALLER_TIMEOUT_MS = 20_000;

    private long delayAfterFailures(int failures) {
        AdaptiveDelayFilter filter = new AdaptiveDelayFilter();
        setFailures(filter, failures, Instant.now());
        return invokeDelay(filter);
    }

    @SuppressWarnings("unchecked")
    private void setFailures(AdaptiveDelayFilter filter, int failures, Instant lastFailureAt) {
        ((AtomicInteger) ReflectionTestUtils.getField(filter, "consecutiveFailures")).set(failures);
        ((AtomicReference<Instant>) ReflectionTestUtils.getField(filter, "lastFailureAt")).set(lastFailureAt);
    }

    private long invokeDelay(AdaptiveDelayFilter filter) {
        return (long) ReflectionTestUtils.invokeMethod(filter, "calculateAdaptiveDelay");
    }

    @Test
    @DisplayName("a healthy client waits only a moment")
    void healthyClientIsFast() {
        assertThat(delayAfterFailures(0)).isLessThan(2_000);
    }

    @Test
    @DisplayName("the delay never reaches the caller's timeout, however many failures")
    void delayNeverStarvesTheCaller() {
        // The whole bug: at 30s of pre-request delay against a 20s timeout,
        // the request was abandoned before it was ever sent.
        for (int failures : new int[] {1, 3, 5, 10, 50, 1000, Integer.MAX_VALUE}) {
            long delay = delayAfterFailures(failures);
            assertThat(delay)
                    .as("delay after %d failures must leave the caller room to actually send", failures)
                    .isLessThan(SHORTEST_CALLER_TIMEOUT_MS);
        }
    }

    @Test
    @DisplayName("backoff grows with failures, then stops growing")
    void backoffIsBoundedAndMonotonic() {
        long one = delayAfterFailures(1);
        long three = delayAfterFailures(3);
        long huge = delayAfterFailures(10_000);

        assertThat(three).isGreaterThan(one);
        // Clamped, not unbounded — 2^10000 must not be computed as a delay.
        assertThat(huge).isEqualTo(delayAfterFailures(5));
    }

    @Test
    @DisplayName("failures decay, so recovery does not depend on a success")
    void failuresDecayOverTime() {
        AdaptiveDelayFilter filter = new AdaptiveDelayFilter();

        // Failures old enough to have decayed. Previously the only reset was a
        // successful response — which the backoff itself had made impossible.
        setFailures(filter, 5, Instant.now().minusSeconds(120));

        assertThat(invokeDelay(filter))
                .as("a client with only stale failures must return to normal spacing on its own")
                .isLessThan(2_000);
    }

    @Test
    @DisplayName("recent failures still count")
    void recentFailuresStillBackOff() {
        AdaptiveDelayFilter filter = new AdaptiveDelayFilter();
        setFailures(filter, 3, Instant.now());

        assertThat(invokeDelay(filter)).isGreaterThan(2_000);
    }
}
