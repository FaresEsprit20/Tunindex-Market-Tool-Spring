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

    /** Attempts for data endpoints, which are worth waiting out a 429 for. */
    @Value("${market-tool.scraping.data-retry-attempts:3}")
    private int dataRetryAttempts;

    /** Base pause after a 429, multiplied by the attempt number. */
    @Value("${market-tool.scraping.rate-limit-backoff-ms:4000}")
    private long rateLimitBackoffMs;

    /**
     * How long a host is left alone after it throttles us out of retries.
     *
     * <p>Long enough that we stop being part of the host's problem, short
     * enough that a brief throttle does not cost a panel its data for the
     * afternoon. Any success clears it early, so the cost of guessing high is
     * only that the first caller after recovery gets stale data.
     */
    @Value("${market-tool.scraping.rate-limit-cooldown-ms:600000}")
    private long rateLimitCooldownMs;

    /**
     * Total wall-clock budget for one {@link #fetchData} call, retries and
     * pacing included.
     *
     * <p>These endpoints are called while somebody waits for a page. Retrying
     * politely past the point where the caller has given up helps nobody: the
     * answer arrives after the response has already gone out, and the work is
     * thrown away. Better to fail inside the budget and let the cooldown stop
     * the next caller from repeating it.
     */
    @Value("${market-tool.scraping.data-fetch-budget-ms:9000}")
    private long dataFetchBudgetMs;

    /**
     * Per-host minimum gaps, as {@code host=millis} pairs.
     *
     * <p>One global interval does not fit every publisher. Yahoo's chart API
     * returns 429 for a burst but is perfectly happy at roughly six seconds
     * between calls — the throttling we saw was self-inflicted, not a block.
     * Slowing every host to Yahoo's tolerance would make the stock sweep
     * needlessly long, so the gap is set per host instead.
     */
    @Value("${market-tool.scraping.host-delays:query1.finance.yahoo.com=6000}")
    private String hostDelayOverrides;

    private final Map<String, Long> hostDelays = new ConcurrentHashMap<>();

    /**
     * The earliest instant each host may next be contacted. Replaces a
     * "last request" timestamp, which could not express a slot already
     * claimed by a thread that has not sent its request yet.
     */
    private final Map<String, Instant> nextAllowedByHost = new ConcurrentHashMap<>();

    /**
     * Hosts that are refusing us, and when to try again.
     *
     * <p>Separate from the pacing map above: that one spaces requests we
     * intend to make, this one records hosts we have decided not to ask at
     * all for a while.
     */
    private final Map<String, Instant> coolingDownUntil = new ConcurrentHashMap<>();

    /** Per-host locks guarding slot reservation in {@link #pace}. */
    private final Map<String, Object> hostLocks = new ConcurrentHashMap<>();
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
            // HTTP/1.1, not the JDK's default HTTP/2.
            //
            // Yahoo answered 429 to this client for weeks while answering 200
            // to the identical URL and headers from curl, seconds apart on the
            // same address. The only remaining difference was the protocol:
            // this client negotiates HTTP/2, curl here is built without it.
            // A Java HTTP/2 connection is distinguishable from a browser's by
            // its settings frames and header ordering, and that is evidently
            // what was being refused - the "rate limit" was never about rate.
            //
            // The cost is a little efficiency on a workload that is paced to
            // one request every few seconds anyway; every host we read speaks
            // HTTP/1.1.
            .version(HttpClient.Version.HTTP_1_1)
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

    /**
     * A paced HTTP GET with no HTML interpretation, for endpoints that return
     * data rather than pages — the World Bank JSON API, for instance.
     *
     * <p>Deliberately skips the challenge detection in {@link #fetch}: those
     * checks look for markers in HTML, and running them over a JSON document
     * is at best meaningless and at worst another false positive. There is no
     * browser escalation here either — an API that refuses us will not be
     * persuaded by Chrome.
     */
    public String fetchData(String url) {
        String host = hostOf(url);

        // A host that has already refused everything recently is not asked
        // again. Without this the retry loop below runs in full for every
        // caller, every time: measured against Yahoo while it was throttling
        // this machine, four quotes took 45-90 seconds to all fail, the
        // caller's timeout expired, and two dashboard panels served a 500
        // instead of the cached figures sitting right there. Backing off is
        // also what the host asked for.
        if (isCoolingDown(host)) {
            log.debug("Skipping {} - {} is in cooldown until {}", url, host, coolingDownUntil.get(host));
            return null;
        }

        // Everything below has to finish inside this. The retry schedule alone
        // can run to about seventy seconds against a throttling host, and the
        // callers here are serving a web request - they give up long before
        // that and, crucially, they give up *before the loop reaches its last
        // attempt*, which is where the cooldown used to be set. So the back-off
        // never armed, and every subsequent request paid the same wait again.
        long deadline = System.currentTimeMillis() + dataFetchBudgetMs;
        int lastStatus = 0;

        for (int attempt = 1; attempt <= dataRetryAttempts; attempt++) {
            pace(host);
            // true: this path serves JSON APIs, so it asks like a page's own
            // script rather than like someone typing a URL.
            Result result = request(url, true);
            lastStatus = result.status();
            if (result.body() != null) {
                clearCooldown(host);
                return result.body();
            }

            boolean lastAttempt = attempt == dataRetryAttempts;
            // 429 is the one refusal worth waiting out: it means "later", not
            // "no". Anything else is retried too, but a rate limit is the case
            // this loop exists for — Yahoo throttles a burst of seven quotes.
            long wait = result.status() == 429
                    ? rateLimitBackoffMs * attempt
                    : 500L * attempt;
            boolean outOfTime = System.currentTimeMillis() + wait >= deadline;

            if (lastAttempt || outOfTime) {
                if (isThrottle(lastStatus)) {
                    // Out of attempts or out of time against a host that is
                    // refusing us. Either way this is no longer a busy moment,
                    // it is a host declining to serve us for now.
                    beginCooldown(host, lastStatus);
                }
                break;
            }

            log.debug("Retrying {} in {}ms (attempt {} of {}, last status {})",
                    url, wait, attempt, dataRetryAttempts, result.status());
            sleep(wait);
        }
        log.warn("Gave up on {} (last status {})", url, lastStatus);
        return null;
    }

    private boolean isThrottle(int status) {
        return status == 429 || status == 403;
    }

    /**
     * The site a data call would plausibly have come from.
     *
     * <p>An API host is usually a subdomain of the site that calls it -
     * query1.finance.yahoo.com is fetched by finance.yahoo.com - so the parent
     * domain is the honest referer. Falls back to the host's own root when the
     * name is too short to have a parent.
     */
    private String refererFor(String url) {
        String host = hostOf(url);
        String[] parts = host.split("\\.");
        if (parts.length >= 3) {
            // query1.finance.yahoo.com -> https://finance.yahoo.com/
            return "https://" + String.join(".", java.util.Arrays.copyOfRange(parts, 1, parts.length)) + "/";
        }
        return "https://" + host + "/";
    }

    /** Whether this host is being left alone for the moment. */
    private boolean isCoolingDown(String host) {
        Instant until = coolingDownUntil.get(host);
        if (until == null) {
            return false;
        }
        if (Instant.now().isAfter(until)) {
            coolingDownUntil.remove(host);
            return false;
        }
        return true;
    }

    private void beginCooldown(String host, int status) {
        Instant until = Instant.now().plusMillis(rateLimitCooldownMs);
        coolingDownUntil.put(host, until);
        log.warn("{} returned {} after {} attempts - backing off until {}",
                host, status, dataRetryAttempts, until);
    }

    /**
     * Ends a cooldown early on the first success.
     *
     * <p>The window is a guess at how long the host wants to be left alone.
     * When it answers before the guess runs out, the guess was wrong and
     * holding to it would keep refusing callers a working host would serve.
     */
    private void clearCooldown(String host) {
        if (coolingDownUntil.remove(host) != null) {
            log.info("{} is answering again - cooldown lifted", host);
        }
    }

    /** A fetch outcome, so a caller can distinguish "later" from "no". */
    private record Result(int status, String body) {
    }

    /** Plain HTTP. Returns null when the response is missing, refused or a challenge page. */
    /**
     * A paced GET, retrying a refusal that means "later" rather than "no".
     *
     * <p>This used to be a single attempt, and the retry loop existed only on
     * the data path - so a rate-limited <em>page</em> failed outright. Under
     * sustained scraping that showed up as forty of seventy-three symbols
     * failing in one run: not a block, just a busy host being asked again too
     * soon and never asked twice.
     *
     * <p>Only 429 and the 5xx family are retried. A 404 is a real answer and
     * repeating it wastes a request the host would rather not serve.
     */
    private String viaHttp(String url) {
        String host = hostOf(url);
        for (int attempt = 1; attempt <= dataRetryAttempts; attempt++) {
            if (attempt > 1) {
                // Claims a fresh slot, so a retry queues with everyone else
                // rather than jumping the gap another thread reserved.
                pace(host);
            }
            Result result = request(url);
            if (result.body() != null) {
                return result.body();
            }
            if (!worthRetrying(result.status()) || attempt == dataRetryAttempts) {
                return null;
            }
            // Backs off further each time: a host that is throttling wants
            // less traffic, so trying again at the same cadence is the one
            // response guaranteed not to help.
            long wait = (result.status() == 429 || result.status() == 403)
                    ? rateLimitBackoffMs * attempt
                    : 500L * attempt;
            log.debug("Retrying {} in {}ms (attempt {} of {}, last status {})",
                    url, wait, attempt, dataRetryAttempts, result.status());
            sleep(wait);
        }
        return null;
    }

    /**
     * True for refusals worth waiting out.
     *
     * <p>403 is included because on this host it is volume-based rather than
     * permanent: the same URLs that return 403 during a run answer 200 when
     * requested on their own. A 404 is a real answer and is not retried -
     * asking again only spends a request the host would rather not serve.
     */
    private boolean worthRetrying(int status) {
        return status == 429 || status == 403 || status >= 500;
    }

    private Result request(String url) {
        return request(url, false);
    }

    /**
     * @param dataEndpoint true for a JSON API, false for an HTML page
     *
     * <p>The distinction is not cosmetic. The headers below describe what kind
     * of request a browser thinks it is making, and sending the page set at a
     * JSON API is a contradiction the API can see: {@code Sec-Fetch-Dest:
     * document} with {@code Sec-Fetch-Site: none} says "the user typed this
     * into the address bar", which nothing legitimately does to a quote
     * endpoint.
     *
     * <p>Yahoo answered 429 to every such request while answering 200 to the
     * same URL with XHR headers and a referer - from the same address, seconds
     * apart. It was read for weeks as an IP rate limit, which is why gold,
     * silver and the dinar crosses were missing from the dashboard: we were
     * being refused for how we asked, not how often.
     */
    private Result request(String url, boolean dataEndpoint) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(httpTimeoutSeconds))
                    // Headers a real Chrome sends. Ordinary browser traffic is
                    // the goal — not evasion of a block, but simply not looking
                    // like a bare library default.
                    .header("User-Agent", userAgent())
                    .header("Accept-Language", "en-US,en;q=0.9,fr;q=0.8")
                    // identity, not gzip: the shared client has no decompressor
                    // wired in, and a gzipped body reaches the parser as raw
                    // bytes — which is what silently broke two thirds of the
                    // exchange once already.
                    .header("Accept-Encoding", "identity");

            if (dataEndpoint) {
                // What a page's own script looks like when it fetches data.
                builder.header("Accept", "application/json,text/plain,*/*")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-site")
                        // Several data hosts check that the call came from
                        // their own site. Derived from the URL rather than
                        // hard-coded so this holds for every provider.
                        .header("Referer", refererFor(url));
            } else {
                builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,"
                                + "image/avif,image/webp,image/apng,*/*;q=0.8")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "none")
                        .header("Sec-Fetch-User", "?1");
            }

            HttpRequest request = builder.GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 403 || status == 429 || status == 503) {
                log.warn("HTTP {} for {} — treating as refused", status, url);
                return new Result(status, null);
            }
            if (status != 200) {
                log.debug("HTTP {} for {}", status, url);
                return new Result(status, null);
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return new Result(status, null);
            }
            if (captchaDetector.isBlocked(body) || captchaDetector.hasCaptcha(body)) {
                log.warn("Challenge page returned for {} — treating as refused", url);
                return new Result(status, null);
            }
            return new Result(status, body);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(0, null);
        } catch (Exception e) {
            log.warn("HTTP fetch failed for {}: {}", url, e.getMessage());
            return new Result(0, null);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
    /**
     * Holds this thread until the host's next free slot, and reserves it.
     *
     * <p>The reservation is the point. This previously read the last request
     * time, slept, and then wrote the new time - three steps with no lock
     * between them. Run from several workers at once, every thread read the
     * same timestamp, computed the same wait, slept the same length and woke
     * together: the effect was not one request every gap but a simultaneous
     * burst of five, every gap. Widening the gap only spaced the bursts
     * further apart, which is why a host kept returning 403 to the pipeline
     * while answering the identical URLs individually with 200 - a burst is
     * what a WAF reacts to, not a total.
     *
     * <p>Now each caller claims the next slot under a per-host lock and then
     * sleeps outside it, so concurrent workers queue up at one gap apart
     * instead of stacking on the same instant. The lock is held only for the
     * arithmetic, never across the sleep.
     */
    private void pace(String host) {
        long wait;
        long gap = delayFor(host) + ThreadLocalRandom.current().nextLong(jitterMs + 1);

        synchronized (lockFor(host)) {
            Instant now = Instant.now();
            Instant earliest = nextAllowedByHost.get(host);
            Instant slot = (earliest == null || earliest.isBefore(now)) ? now : earliest;

            wait = Duration.between(now, slot).toMillis();
            // Claimed before releasing the lock, so the next caller queues
            // behind this slot rather than racing for the same one.
            nextAllowedByHost.put(host, slot.plusMillis(gap));
        }

        if (wait > 0) {
            sleep(wait);
        }
    }

    /** One lock per host, so unrelated hosts never wait on each other. */
    private Object lockFor(String host) {
        return hostLocks.computeIfAbsent(host, key -> new Object());
    }

    /** This host's configured gap, falling back to the global default. */
    private long delayFor(String host) {
        if (hostDelays.isEmpty() && hostDelayOverrides != null && !hostDelayOverrides.isBlank()) {
            for (String pair : hostDelayOverrides.split(",")) {
                String[] parts = pair.split("=", 2);
                if (parts.length == 2) {
                    try {
                        hostDelays.put(parts[0].trim(), Long.parseLong(parts[1].trim()));
                    } catch (NumberFormatException e) {
                        log.warn("Ignoring malformed host delay '{}'", pair);
                    }
                }
            }
        }
        return hostDelays.getOrDefault(host, hostDelayMs);
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
