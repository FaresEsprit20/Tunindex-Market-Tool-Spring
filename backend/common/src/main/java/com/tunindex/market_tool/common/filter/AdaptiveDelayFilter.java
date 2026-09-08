package com.tunindex.market_tool.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spaces outbound requests, and backs off when a host starts refusing.
 *
 * <p><b>This filter previously deadlocked the entire scraper</b>, and the way
 * it did so is worth keeping written down, because the shape of the bug is
 * easy to reintroduce:
 *
 * <ol>
 *   <li>The delay was applied <em>before</em> the request was sent, and grew
 *       as {@code 2^failures} seconds, capped at 30s.
 *   <li>Callers time out well under that — the World Bank provider at 20s.
 *   <li>So once the delay passed the caller's timeout, every request timed out
 *       <em>while still waiting to be sent</em>. The request never left.
 *   <li>Each of those timeouts counted as another failure, and the only thing
 *       that reset the counter was a success — which was now impossible.
 * </ol>
 *
 * <p>The scraper looked exactly like it had been blocked by the sites. It had
 * not: it had throttled itself into a state with no exit.
 *
 * <p>Three things prevent that here. The backoff is capped far below any
 * caller's timeout, so a delay can never consume the whole budget. The failure
 * count decays with time, so recovery does not depend on a success that
 * cannot happen. And the count is clamped, so the exponent cannot run away.
 */
@Slf4j
public class AdaptiveDelayFilter implements ExchangeFilterFunction {

    /**
     * Ceiling on the pre-request delay.
     *
     * <p>Deliberately well under the shortest caller timeout (20s). A delay
     * that can exceed the caller's budget is always a bug: it converts a slow
     * request into one that is never attempted at all.
     */
    private static final long MAX_DELAY_MS = 8_000;

    /** Clamp on the exponent, so the backoff cannot overflow into nonsense. */
    private static final int MAX_TRACKED_FAILURES = 5;

    /**
     * How long a single failure keeps counting.
     *
     * <p>This is the property the old version lacked entirely. Without decay,
     * a bad minute poisons the client permanently — the counter only ever went
     * up unless a request succeeded, and the backoff was what prevented that.
     */
    private static final Duration FAILURE_DECAY = Duration.ofSeconds(60);

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        long delay = calculateAdaptiveDelay();

        if (delay > 1_000) {
            log.debug("Backing off {}ms before {} ({} recent failures)",
                    delay, request.url(), consecutiveFailures.get());
        }

        return Mono.delay(Duration.ofMillis(delay))
                .then(next.exchange(request))
                .doOnError(error -> recordFailure())
                .doOnSuccess(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        consecutiveFailures.set(0);
                        requestCount.incrementAndGet();
                    } else {
                        recordFailure();
                    }
                });
    }

    private void recordFailure() {
        lastFailureAt.set(Instant.now());
        int failures = consecutiveFailures.incrementAndGet();
        if (failures == 1 || failures % 5 == 0) {
            log.warn("Outbound request failed — {} consecutive failure(s)", failures);
        }
    }

    private long calculateAdaptiveDelay() {
        int failures = effectiveFailures();

        // Ordinary spacing: short, with jitter so requests are not evenly
        // spaced, which is itself a recognisable automation signature.
        long delay = 100 + ThreadLocalRandom.current().nextInt(400);

        if (failures > 0) {
            int exponent = Math.min(failures, MAX_TRACKED_FAILURES);
            delay = (long) Math.pow(2, exponent) * 1000L;
        }

        // An occasional longer pause, so the cadence is not perfectly uniform.
        if (requestCount.get() % 10 == 0) {
            delay += 500 + ThreadLocalRandom.current().nextInt(1000);
        }

        return Math.min(delay, MAX_DELAY_MS);
    }

    /**
     * Failures still recent enough to matter. Once the decay window passes
     * with no new failure, the counter is cleared and the client returns to
     * normal spacing on its own — no successful request required.
     */
    private int effectiveFailures() {
        int failures = consecutiveFailures.get();
        if (failures == 0) {
            return 0;
        }
        Instant last = lastFailureAt.get();
        if (last != null && Duration.between(last, Instant.now()).compareTo(FAILURE_DECAY) > 0) {
            consecutiveFailures.set(0);
            log.info("No failures for {}s — clearing backoff", FAILURE_DECAY.toSeconds());
            return 0;
        }
        return failures;
    }
}
