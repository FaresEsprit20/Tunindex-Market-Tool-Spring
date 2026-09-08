package com.tunindex.market_tool.collector.webscraping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises an anti-bot interstitial, and — just as importantly — declines to
 * mistake an ordinary page for one.
 *
 * <p><b>Why this was rewritten.</b> The previous version matched bare
 * substrings anywhere in the body, including the literal {@code "429"}. On a
 * financial statistics page that is a guaranteed false positive: BIAT's stock
 * split ratio is {@code 1.1428571429}, so every fetch of its statistics page
 * was reported as blocked. {@code "blocked"}, {@code "forbidden"},
 * {@code "challenge"} and {@code "security check"} were matched the same way
 * and appear in ordinary prose, scripts and CSS class names.
 *
 * <p>Two properties separate a real interstitial from a page that merely
 * mentions one of these words:
 *
 * <ul>
 *   <li><b>It is small.</b> A challenge is a stub — a few kilobytes of notice
 *       and a script. A 115 KB page dense with financial tables is the content
 *       we asked for, whatever words happen to appear inside it.
 *   <li><b>It announces itself up front.</b> The marker sits in the
 *       {@code <title>} or the opening markup, not buried in a data table.
 * </ul>
 *
 * <p>So a match must be a precise phrase <em>and</em> appear in a page that
 * looks like an interstitial. HTTP status codes are not consulted here at all
 * — the caller already has the real status and does not need it inferred from
 * the body.
 */
@Service
@Slf4j
public class CaptchaDetector {

    /**
     * Above this size, treat the response as real content.
     *
     * <p>Cloudflare and reCAPTCHA interstitials run well under 30 KB; the
     * pages we actually want run from 30 KB to well over 100 KB. Generous on
     * purpose — a false negative costs one wasted parse, while a false
     * positive diverts an entire host onto the browser path.
     */
    private static final int MAX_INTERSTITIAL_BYTES = 60_000;

    /** How much of the opening markup counts as "up front". */
    private static final int HEAD_WINDOW = 4_000;

    /**
     * Phrases specific enough to be meaningful on their own. Deliberately
     * excludes bare words — "blocked", "forbidden", "challenge", "429" — which
     * carry no signal outside a challenge page's own wording.
     */
    private static final List<String> CAPTCHA_PHRASES = List.of(
            "verify you are human",
            "please confirm you are human",
            "i'm not a robot",
            "are you a robot",
            "robot check",
            "human verification",
            "verify your identity",
            "g-recaptcha",
            "h-captcha",
            "hcaptcha.com",
            "recaptcha/api.js",
            "cf-challenge",
            "challenge-platform",
            "just a moment...");

    private static final List<String> BLOCK_PHRASES = List.of(
            "access denied",
            "attention required",
            "your request has been blocked",
            "rate limit exceeded",
            "too many requests",
            "unusual traffic from your computer",
            "has been temporarily blocked",
            "you have been blocked",
            "sorry, you have been blocked");

    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** True when the response looks like a CAPTCHA interstitial. */
    public boolean hasCaptcha(String html) {
        return matches(html, CAPTCHA_PHRASES, "captcha");
    }

    /** True when the response looks like an outright block page. */
    public boolean isBlocked(String html) {
        return matches(html, BLOCK_PHRASES, "block");
    }

    /**
     * Rate limiting specifically, for a caller that wants to back off rather
     * than escalate. Kept separate because the right response differs: waiting
     * helps here, whereas a CAPTCHA needs a different route entirely.
     */
    public boolean isRateLimited(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase();
        return looksLikeInterstitial(html, lower)
                && (lower.contains("rate limit exceeded")
                || lower.contains("too many requests")
                || lower.contains("unusual traffic from your computer"));
    }

    /** Which phrase tripped the detector, for logs. Null when nothing did. */
    public String getCaptchaType(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        String lower = html.toLowerCase();
        if (!looksLikeInterstitial(html, lower)) {
            return null;
        }
        for (String phrase : CAPTCHA_PHRASES) {
            if (lower.contains(phrase)) {
                return phrase;
            }
        }
        return null;
    }

    private boolean matches(String html, List<String> phrases, String kind) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase();
        if (!looksLikeInterstitial(html, lower)) {
            return false;
        }
        for (String phrase : phrases) {
            if (lower.contains(phrase)) {
                log.debug("Detected {} interstitial on phrase '{}'", kind, phrase);
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the response has the shape of an interstitial at all: either
     * small enough to be a stub, or carrying the giveaway in its title.
     *
     * <p>This gate is what stops a legitimate page being condemned by a
     * substring buried in its data.
     */
    private boolean looksLikeInterstitial(String html, String lower) {
        if (html.length() <= MAX_INTERSTITIAL_BYTES) {
            return true;
        }
        String title = titleOf(lower);
        if (title == null) {
            return false;
        }
        // A large page still counts if its title says so — some block pages
        // ship a heavy script bundle alongside the notice.
        return title.contains("just a moment")
                || title.contains("attention required")
                || title.contains("access denied")
                || title.contains("blocked")
                || title.contains("captcha");
    }

    private String titleOf(String lowerHtml) {
        Matcher matcher = TITLE.matcher(lowerHtml);
        if (!matcher.find()) {
            return null;
        }
        String title = matcher.group(1).trim();
        return title.length() > HEAD_WINDOW ? title.substring(0, HEAD_WINDOW) : title;
    }
}
