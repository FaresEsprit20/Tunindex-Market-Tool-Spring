package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.webscraping.CaptchaDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the detector against crying wolf.
 *
 * <p>A false positive here is expensive and invisible: the fetcher concludes
 * the host is blocking us, diverts every subsequent request onto a browser,
 * and the scrape appears to be under an anti-bot wall that does not exist.
 * That is exactly what happened — the block list contained the bare string
 * "429", and BIAT's stock split ratio is 1.1428571429.
 */
@DisplayName("CaptchaDetector")
class CaptchaDetectorTest {

    private final CaptchaDetector detector = new CaptchaDetector();

    /** A content page: large, data-dense, and mentioning awkward substrings. */
    private String financialPage(String extra) {
        StringBuilder page = new StringBuilder();
        page.append("<html><head><title>BIAT (BVMT) Statistics</title></head><body>");
        page.append("<table><tr><td>Split Ratio</td><td>1.1428571429</td></tr>");
        page.append("<tr><td>Market Cap</td><td>3,210,000,000</td></tr></table>");
        page.append(extra);
        // Pad past the interstitial size ceiling, as a real page would be.
        page.append("<div>").append("financial data ".repeat(6000)).append("</div>");
        page.append("</body></html>");
        return page.toString();
    }

    @Test
    @DisplayName("a split ratio containing 429 is not a block")
    void splitRatioIsNotABlock() {
        // The exact production false positive.
        String page = financialPage("");

        assertThat(page).contains("1.1428571429");
        assertThat(detector.isBlocked(page)).isFalse();
        assertThat(detector.hasCaptcha(page)).isFalse();
        assertThat(detector.isRateLimited(page)).isFalse();
    }

    @Test
    @DisplayName("ordinary prose using these words is not a block")
    void prosePassesThrough() {
        String page = financialPage(
                "<p>The regulator blocked the merger; the transfer was forbidden "
                        + "pending a security check, and the challenge was dismissed.</p>");

        assertThat(detector.isBlocked(page)).isFalse();
        assertThat(detector.hasCaptcha(page)).isFalse();
    }

    @Test
    @DisplayName("a Cloudflare interstitial is caught")
    void cloudflareInterstitial() {
        String page = "<html><head><title>Just a moment...</title></head>"
                + "<body><div class=\"cf-challenge\">Checking your browser…</div></body></html>";

        assertThat(detector.hasCaptcha(page)).isTrue();
    }

    @Test
    @DisplayName("an access-denied stub is caught")
    void accessDeniedStub() {
        String page = "<html><head><title>Access denied</title></head>"
                + "<body><h1>Sorry, you have been blocked</h1></body></html>";

        assertThat(detector.isBlocked(page)).isTrue();
    }

    @Test
    @DisplayName("a reCAPTCHA widget is caught")
    void recaptchaWidget() {
        String page = "<html><body><div class=\"g-recaptcha\"></div>"
                + "<p>Please confirm you are human</p></body></html>";

        assertThat(detector.hasCaptcha(page)).isTrue();
        assertThat(detector.getCaptchaType(page)).isNotNull();
    }

    @Test
    @DisplayName("rate limiting is reported separately from a captcha")
    void rateLimitStub() {
        String page = "<html><head><title>Too Many Requests</title></head>"
                + "<body><p>Rate limit exceeded. Try again later.</p></body></html>";

        assertThat(detector.isRateLimited(page)).isTrue();
        assertThat(detector.isBlocked(page)).isTrue();
    }

    @Test
    @DisplayName("a large page still counts when its own title says it is blocked")
    void largePageWithBlockingTitle() {
        // Some block pages ship a heavy script bundle; the title is the tell.
        String page = "<html><head><title>Attention Required! | Cloudflare</title></head><body>"
                + "<p>Sorry, you have been blocked</p>"
                + "<script>" + "x".repeat(80_000) + "</script></body></html>";

        assertThat(page.length()).isGreaterThan(60_000);
        assertThat(detector.isBlocked(page)).isTrue();
    }

    @Test
    @DisplayName("null and empty are never a block")
    void nullAndEmpty() {
        assertThat(detector.isBlocked(null)).isFalse();
        assertThat(detector.isBlocked("")).isFalse();
        assertThat(detector.hasCaptcha(null)).isFalse();
        assertThat(detector.getCaptchaType(null)).isNull();
    }
}
