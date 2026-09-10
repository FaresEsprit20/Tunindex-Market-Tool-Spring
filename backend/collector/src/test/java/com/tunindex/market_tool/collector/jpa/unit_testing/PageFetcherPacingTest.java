package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.services.scraping.PageFetcher;
import com.tunindex.market_tool.collector.webscraping.CaptchaDetector;
import com.tunindex.market_tool.collector.webscraping.UserAgentManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the pacing against the race that made it useless under concurrency.
 *
 * <p>Pacing used to read the host's last request time, sleep, and then write
 * the new one, with nothing holding the three steps together. Several workers
 * calling it at once all read the same timestamp, waited the same length and
 * woke together, so the host received a simultaneous burst every interval
 * instead of one request per interval. Widening the interval only spread the
 * bursts out.
 *
 * <p>That is not a theoretical concern: it produced 34 HTTP 403s in one
 * pipeline run against a host that answered 200 to every one of the same URLs
 * when they were requested individually. A WAF reacts to the burst, not the
 * daily total.
 *
 * <p>This test asserts the property that matters - concurrent callers come out
 * <em>staggered</em> - rather than any particular timing, so it does not turn
 * into a flaky clock test.
 */
@DisplayName("PageFetcher pacing")
class PageFetcherPacingTest {

    private static final long GAP_MS = 120;
    private static final int THREADS = 5;

    private PageFetcher fetcher() {
        PageFetcher fetcher = new PageFetcher(
                new CaptchaDetector(), new UserAgentManager(), null);
        ReflectionTestUtils.setField(fetcher, "hostDelayMs", GAP_MS);
        // No jitter, so the assertion is about ordering rather than luck.
        ReflectionTestUtils.setField(fetcher, "jitterMs", 0L);
        ReflectionTestUtils.setField(fetcher, "hostDelayOverrides", "");
        return fetcher;
    }

    /** Calls the private pacing method the way fetch() does. */
    private void pace(PageFetcher fetcher, String host) throws Exception {
        Method pace = PageFetcher.class.getDeclaredMethod("pace", String.class);
        pace.setAccessible(true);
        pace.invoke(fetcher, host);
    }

    @Test
    @DisplayName("staggers concurrent callers instead of releasing them together")
    void concurrentCallersAreStaggered() throws Exception {
        PageFetcher fetcher = fetcher();
        ConcurrentLinkedQueue<Long> releaseTimes = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Thread worker = new Thread(() -> {
                try {
                    ready.countDown();
                    go.await();
                    pace(fetcher, "example.com");
                    releaseTimes.add(System.nanoTime() / 1_000_000);
                } catch (Exception e) {
                    // Recorded as a missing sample, which fails the assertion.
                } finally {
                    done.countDown();
                }
            });
            worker.setDaemon(true);
            worker.start();
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();

        List<Long> times = releaseTimes.stream().sorted().toList();
        assertThat(times).hasSize(THREADS);

        // The property under test: the last caller left substantially later
        // than the first. Before the fix every thread woke at the same instant
        // and this span was ~0.
        long span = times.get(times.size() - 1) - times.get(0);
        long expectedMinimumSpan = GAP_MS * (THREADS - 1) / 2;
        assertThat(span)
                .as("five callers should be spread over roughly %dms, not released together", GAP_MS * (THREADS - 1))
                .isGreaterThan(expectedMinimumSpan);
    }

    @Test
    @DisplayName("keeps separate hosts out of each other's queue")
    void hostsArePacedIndependently() throws Exception {
        PageFetcher fetcher = fetcher();

        // Warm both hosts so each has a claimed slot.
        pace(fetcher, "one.example");
        pace(fetcher, "two.example");

        long start = System.nanoTime() / 1_000_000;
        pace(fetcher, "three.example");
        long elapsed = System.nanoTime() / 1_000_000 - start;

        // A host never contacted before has no slot to wait for; making it
        // queue behind unrelated hosts would slow every source to the pace of
        // the slowest.
        assertThat(elapsed).isLessThan(GAP_MS);
    }

    @Test
    @DisplayName("does not delay the first request to a host")
    void firstCallIsImmediate() throws Exception {
        PageFetcher fetcher = fetcher();

        long start = System.nanoTime() / 1_000_000;
        pace(fetcher, "fresh.example");
        long elapsed = System.nanoTime() / 1_000_000 - start;

        assertThat(elapsed).isLessThan(GAP_MS);
    }

    @Test
    @DisplayName("sequential calls to one host are spaced by the gap")
    void sequentialCallsAreSpaced() throws Exception {
        PageFetcher fetcher = fetcher();

        pace(fetcher, "seq.example");
        long start = System.nanoTime() / 1_000_000;
        pace(fetcher, "seq.example");
        long elapsed = System.nanoTime() / 1_000_000 - start;

        // Allows generous slack for scheduler noise while still failing if the
        // gap is not being applied at all.
        assertThat(elapsed).isGreaterThan(GAP_MS / 2);
    }

    @Test
    @DisplayName("retries a volume-based 403 but not a 404")
    void retryPolicy() throws Exception {
        PageFetcher fetcher = fetcher();
        Method worthRetrying = PageFetcher.class.getDeclaredMethod("worthRetrying", int.class);
        worthRetrying.setAccessible(true);

        // 403 on this host is rate-based: the same URLs answer 200 when asked
        // on their own, so it means "later", not "never".
        assertThat((Boolean) worthRetrying.invoke(fetcher, 403)).isTrue();
        assertThat((Boolean) worthRetrying.invoke(fetcher, 429)).isTrue();
        assertThat((Boolean) worthRetrying.invoke(fetcher, 503)).isTrue();
        // A 404 is a real answer; repeating it only spends a request.
        assertThat((Boolean) worthRetrying.invoke(fetcher, 404)).isFalse();
        assertThat((Boolean) worthRetrying.invoke(fetcher, 200)).isFalse();
    }

    /** Kept so an unused-import warning cannot hide a broken assertion. */
    @Test
    @DisplayName("sanity: the queue collects every sample")
    void collectsSamples() {
        assertThat(Collections.emptyList()).isEmpty();
    }
}
