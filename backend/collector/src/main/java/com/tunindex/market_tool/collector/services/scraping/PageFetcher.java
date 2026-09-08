package com.tunindex.market_tool.collector.services.scraping;

import com.tunindex.market_tool.collector.webscraping.CaptchaDetector;
import com.tunindex.market_tool.collector.webscraping.UserAgentManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One way in for every page this service scrapes, with a deliberate order of
 * preference: a plain HTTP request first, a real browser only if that is
 * actually refused.
 *
 * <p><b>Why this exists.</b> Both sources we scrape answer a plain HTTP GET in
 * under half a second with the full markup we parse. Driving them through
 * Selenium instead cost a Chrome launch per page — hundreds of megabytes and
 * several seconds each, ~69 times per refresh pass — which is both far slower
 * and considerably more likely to look like abuse than the requests it
 * replaced. A browser is the right tool when a site genuinely requires
 * JavaScript or fingerprints the client; it is the wrong default.
 *
 * <p><b>How escalation works.</b> A host starts on the HTTP path. If a
 * response comes back refused — a 403/429, or a body the
 * {@link CaptchaDetector} recognises as a challenge — that host is marked as
 * needing a browser and subsequent fetches go straight to Selenium, so we stop
 * spending a doomed request first. The mark expires, so a site that blocked us
 * during one bad hour is retried on the cheap path later instead of being
 * written off forever.
 *
 * <p><b>On not getting blocked.</b> The pacing here is per host rather than
 * global, with jitter: a fixed interval is itself a fingerprint, and requests
 * spaced by exactly 1500ms are recognisably automated. Volume is the thing
 * that actually gets a scraper banned, so the interval is deliberately
 * unhurried and the caller is expected to be sequential.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PageFetcher {

    private final CaptchaDetector captchaDetector;
    private final UserAgentManager userAgentManager;
    private final SeleniumService seleniumService;

    /** Minimum gap between two requests to the same host. */
    @Value("${market-tool.scraping.host-delay-ms:1200}")
    private long hostDelayMs;

    /**
     * Random extra delay on top, drawn per request. Exactly-even spacing is a
     * stronger automation signal than the request rate itself.
     */
    @Value("${market-tool.scraping.jitter-ms:800}")
    private long jitterMs;

    /** How long a host stays marked as "needs a browser" before we retry HTTP. */
    @Value("${market-tool.scraping.browser-fallback-minutes:30}")
    private long browserFallbackMinutes;

    /**
     * Browser escalation is <b>opt-in</b>.
     *
     * <p>Off by default because both sources currently answer plain HTTP, and
     * the failure mode when this is wrong is severe: a single mis-detected
     * block marks a whole host as browser-required, and the next pipeline pass
     * — three pages across seventy-odd symbols — becomes two hundred Chrome
     * launches. That is exactly what happened, and it took the machine down.
     * Turn it on deliberately, when a site has actually started refusing.
     */
    @Value("${market-tool.scraping.browser-fallback-enabled:false}")
    private boolean browserFallbackEnabled;

    /**
     * Hard ceiling on browser launches per hour, across all hosts.
     *
     * <p>A budget rather than a rate limit: the point is that no bug —
     * a bad detector, a site returning odd markup, a loop — can turn into an
     * unbounded number of Chrome processes. When it is spent, escalation stops
     * and the pass simply misses those pages, which is recoverable. Chrome
     * exhausting the host is not.
     */
    @Value("${market-tool.scraping.browser-launches-per-hour:12}")
    private int browserLaunchesPerHour;

    @Value("${market-tool.scraping.http-timeout-seconds:30}")
    private long httpTimeoutSeconds;

    private final Map<String, Instant> lastRequestByHost = new ConcurrentHashMap<>();
    private final Map<String, Instant> browserRequiredUntil = new ConcurrentHashMap<>();

    /** Rolling hour for the browser launch budget; guarded by {@code this}. */
    private Instant budgetWindowStart;
    private int browserLaunchesThisHour;

    /**
     * Built once and reused. A fresh client per request would discard the
     * connection pool, so every fetch would pay a new TLS handshake — slower
     * for us and a more conspicuous pattern at the other end.
     *
     * <p>Redirects are followed because both sources redirect between
     * www/non-www and trailing-slash forms.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * The page markup, or null when it could not be obtained by any route.
     *
     * <p>Null rather than an exception because every caller's correct response
     * is the same — carry on without this page — and because a failed scrape
     * is an expected operating condition, not an error.
     */
    public String fetch(String url) {
        String host = hostOf(url);
        pace(host);

        if (browserFallbackEnabled && needsBrowser(host)) {
            log.debug("{} is marked as needing a browser; skipping the HTTP attempt", host);
            return viaBrowser(url);
        }

        String html = viaHttp(url);
        if (html != null) {
            return html;
        }

        if (!browserFallbackEnabled) {
            return null;
        }

        log.info("HTTP fetch refused for {} — escalating to a browser", url);
        markBrowserRequired(host);
        return viaBrowser(url);
    }

    /** Plain HTTP. Returns null when the response is missing, refused or a challenge page. */
    private String viaHttp(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(httpTimeoutSeconds))
                    // Headers a real Chrome sends. Ordinary browser traffic is
                    // the goal — not evasion of a block, but simply not looking
                    // like a bare library default.
                    .header("User-Agent", userAgent())
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,"
                            + "image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,fr;q=0.8")
                    // identity, not gzip: the shared client has no decompressor
                    // wired in, and a gzipped body reaches the parser as raw
                    // bytes — which is what silently broke two thirds of the
                    // exchange once already.
                    .header("Accept-Encoding", "identity")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 403 || status == 429 || status == 503) {
                log.warn("HTTP {} for {} — treating as refused", status, url);
                return null;
            }
            if (status != 200) {
                log.debug("HTTP {} for {}", status, url);
                return null;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return null;
            }
            if (captchaDetector.isBlocked(body) || captchaDetector.hasCaptcha(body)) {
                log.warn("Challenge page returned for {} — treating as refused", url);
                return null;
            }
            return body;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("HTTP fetch failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Whether a browser launch is still within this hour's budget.
     *
     * <p>Synchronised because the check and the increment must be one step:
     * two threads both seeing "one launch left" is how a ceiling of twelve
     * becomes a ceiling of however many threads are running.
     */
    private synchronized boolean claimBrowserLaunch() {
        Instant now = Instant.now();
        if (budgetWindowStart == null || Duration.between(budgetWindowStart, now).toHours() >= 1) {
            budgetWindowStart = now;
            browserLaunchesThisHour = 0;
        }
        if (browserLaunchesThisHour >= browserLaunchesPerHour) {
            return false;
        }
        browserLaunchesThisHour++;
        return true;
    }

    /** The expensive path. Never throws — a browser failure is just another miss. */
    private String viaBrowser(String url) {
        if (!claimBrowserLaunch()) {
            log.warn("Browser budget for this hour is spent ({} launches) — skipping {}. "
                    + "If this repeats, the site is genuinely refusing us and the budget "
                    + "needs raising deliberately, not by accident.", browserLaunchesPerHour, url);
            return null;
        }
        try {
            String html = seleniumService.getPageSource(url);
            if (html != null && !captchaDetector.isBlocked(html)) {
                return html;
            }
            log.warn("Browser fetch for {} also came back blocked or empty", url);
            return null;
        } catch (Exception e) {
            log.warn("Browser fetch failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Per host, not global: pacing ilboursa should not also slow a request to
     * an unrelated publisher, and one shared clock made every source wait on
     * the slowest.
     */
    private void pace(String host) {
        Instant last = lastRequestByHost.get(host);
        long wait = hostDelayMs + ThreadLocalRandom.current().nextLong(jitterMs + 1);

        if (last != null) {
            long elapsed = Duration.between(last, Instant.now()).toMillis();
            wait = Math.max(0, wait - elapsed);
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestByHost.put(host, Instant.now());
    }

    private boolean needsBrowser(String host) {
        Instant until = browserRequiredUntil.get(host);
        if (until == null) {
            return false;
        }
        if (Instant.now().isAfter(until)) {
            // The mark has expired — try the cheap path again. A block is
            // usually a bad hour, not a permanent verdict.
            browserRequiredUntil.remove(host);
            log.info("Retrying the HTTP path for {} — browser fallback window expired", host);
            return false;
        }
        return true;
    }

    private void markBrowserRequired(String host) {
        browserRequiredUntil.put(host, Instant.now().plus(Duration.ofMinutes(browserFallbackMinutes)));
    }

    private String userAgent() {
        try {
            String agent = userAgentManager.getRandomUserAgent();
            if (agent != null && !agent.isBlank()) {
                return agent;
            }
        } catch (Exception e) {
            // Fall through to the constant below.
        }
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36";
    }

    private String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host;
        } catch (Exception e) {
            return url;
        }
    }

    /** Hosts currently on the browser path, for the operational endpoint. */
    public Map<String, Instant> browserRequiredHosts() {
        return Map.copyOf(browserRequiredUntil);
    }
}
