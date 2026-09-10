package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.services.scraping.PageFetcher;
import com.tunindex.market_tool.collector.webscraping.CaptchaDetector;
import com.tunindex.market_tool.collector.webscraping.UserAgentManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the back-off that stops a throttling host from hanging the caller.
 *
 * <p>The failure this exists to prevent was measured, not imagined. While
 * Yahoo was rate-limiting this machine it answered 429 in about 150ms, and the
 * retry loop turned that fast, clear refusal into a 45-90 second wait: four
 * instruments, five attempts each, six seconds of pacing and a growing backoff
 * between them. The caller's timeout expired first and two dashboard panels
 * returned 500 - while a perfectly good cached copy sat unused.
 *
 * <p>So the property under test is not "we retry politely". It is that once a
 * host has refused us out of retries, the next caller is turned away
 * <em>immediately</em> rather than queueing behind the same wait again.
 */
@DisplayName("PageFetcher host cooldown")
class PageFetcherCooldownTest {

    private PageFetcher fetcher() {
        PageFetcher fetcher = new PageFetcher(
                new CaptchaDetector(), new UserAgentManager(), null);
        ReflectionTestUtils.setField(fetcher, "hostDelayMs", 10L);
        ReflectionTestUtils.setField(fetcher, "jitterMs", 0L);
        ReflectionTestUtils.setField(fetcher, "hostDelayOverrides", "");
        ReflectionTestUtils.setField(fetcher, "dataRetryAttempts", 3);
        ReflectionTestUtils.setField(fetcher, "rateLimitBackoffMs", 10L);
        ReflectionTestUtils.setField(fetcher, "rateLimitCooldownMs", 600_000L);
        ReflectionTestUtils.setField(fetcher, "dataFetchBudgetMs", 9_000L);
        ReflectionTestUtils.setField(fetcher, "httpTimeoutSeconds", 5L);
        return fetcher;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Instant> cooldowns(PageFetcher fetcher) {
        return (Map<String, Instant>) ReflectionTestUtils.getField(fetcher, "coolingDownUntil");
    }

    private boolean isCoolingDown(PageFetcher fetcher, String host) throws Exception {
        Method method = PageFetcher.class.getDeclaredMethod("isCoolingDown", String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(fetcher, host);
    }

    private void beginCooldown(PageFetcher fetcher, String host, int status) throws Exception {
        Method method = PageFetcher.class.getDeclaredMethod("beginCooldown", String.class, int.class);
        method.setAccessible(true);
        method.invoke(fetcher, host, status);
    }

    private void clearCooldown(PageFetcher fetcher, String host) throws Exception {
        Method method = PageFetcher.class.getDeclaredMethod("clearCooldown", String.class);
        method.setAccessible(true);
        method.invoke(fetcher, host);
    }

    @Test
    @DisplayName("a host nobody has upset is contacted normally")
    void freshHostIsNotBlocked() throws Exception {
        PageFetcher fetcher = fetcher();

        assertThat(isCoolingDown(fetcher, "example.com")).isFalse();
    }

    @Test
    @DisplayName("a throttled host is skipped rather than queued behind another wait")
    void throttledHostIsSkipped() throws Exception {
        PageFetcher fetcher = fetcher();

        beginCooldown(fetcher, "query1.finance.yahoo.com", 429);

        assertThat(isCoolingDown(fetcher, "query1.finance.yahoo.com")).isTrue();
    }

    @Test
    @DisplayName("the cooldown is per host, so one bad provider does not stop the others")
    void cooldownDoesNotSpreadBetweenHosts() throws Exception {
        // Yahoo throttling us must not stop the BVMT sweep, which is the
        // whole reason the pacing is per host in the first place.
        PageFetcher fetcher = fetcher();

        beginCooldown(fetcher, "query1.finance.yahoo.com", 429);

        assertThat(isCoolingDown(fetcher, "query1.finance.yahoo.com")).isTrue();
        assertThat(isCoolingDown(fetcher, "stockanalysis.com")).isFalse();
    }

    @Test
    @DisplayName("the cooldown lapses on its own once the window passes")
    void cooldownExpires() throws Exception {
        // Otherwise a single throttle would silence a provider until restart.
        PageFetcher fetcher = fetcher();
        cooldowns(fetcher).put("example.com", Instant.now().minusSeconds(1));

        assertThat(isCoolingDown(fetcher, "example.com")).isFalse();
    }

    @Test
    @DisplayName("an expired cooldown is forgotten rather than checked forever")
    void expiredCooldownIsRemoved() throws Exception {
        PageFetcher fetcher = fetcher();
        cooldowns(fetcher).put("example.com", Instant.now().minusSeconds(1));

        isCoolingDown(fetcher, "example.com");

        assertThat(cooldowns(fetcher)).doesNotContainKey("example.com");
    }

    @Test
    @DisplayName("a success lifts the cooldown early")
    void successClearsCooldown() throws Exception {
        // The window is a guess at how long the host wants to be left alone.
        // If it answers sooner, holding to the guess would keep refusing
        // callers a host that is plainly willing to serve them.
        PageFetcher fetcher = fetcher();
        beginCooldown(fetcher, "example.com", 429);

        clearCooldown(fetcher, "example.com");

        assertThat(isCoolingDown(fetcher, "example.com")).isFalse();
    }

    @Test
    @DisplayName("clearing a host that was never cooling down is harmless")
    void clearingUnknownHostIsSafe() throws Exception {
        // Called after every successful fetch, so overwhelmingly on hosts
        // that were never in cooldown at all.
        PageFetcher fetcher = fetcher();

        clearCooldown(fetcher, "never-seen.example");

        assertThat(cooldowns(fetcher)).isEmpty();
    }

    @Test
    @DisplayName("a throttled fetch gives up inside its budget rather than the full retry schedule")
    void fetchStaysWithinBudget() throws Exception {
        // The bug this catches: the cooldown was only armed on the loop's
        // final attempt, but the retry schedule ran to roughly seventy
        // seconds and every caller timed out at thirty - so the last attempt
        // never happened, the cooldown never armed, and the next caller paid
        // the same wait over again. The budget is what makes the back-off
        // reachable.
        PageFetcher fetcher = fetcher();
        ReflectionTestUtils.setField(fetcher, "dataFetchBudgetMs", 1_500L);
        ReflectionTestUtils.setField(fetcher, "rateLimitBackoffMs", 4_000L);

        long start = System.nanoTime();
        // Unroutable, so every attempt fails fast without reaching a network.
        fetcher.fetchData("http://127.0.0.1:9/never-listening");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Comfortably under the ~70s the unbounded retry schedule would take.
        assertThat(elapsedMs).isLessThan(20_000);
    }

    @Test
    @DisplayName("a cooling-down host is refused in well under the retry wait")
    void skipIsImmediate() throws Exception {
        // The point of the whole mechanism: the caller gets its answer back
        // in microseconds instead of spending the retry budget again.
        PageFetcher fetcher = fetcher();
        ReflectionTestUtils.setField(fetcher, "coolingDownUntil", new ConcurrentHashMap<String, Instant>());
        beginCooldown(fetcher, "query1.finance.yahoo.com", 429);

        long start = System.nanoTime();
        String body = fetcher.fetchData("https://query1.finance.yahoo.com/v8/finance/chart/GC=F");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(body).isNull();
        assertThat(elapsedMs).isLessThan(100);
    }
}
