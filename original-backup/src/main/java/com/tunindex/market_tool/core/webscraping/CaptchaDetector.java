package com.tunindex.market_tool.core.webscraping;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class CaptchaDetector {

    private static final List<String> CAPTCHA_TRIGGERS = Arrays.asList(
            "captcha",
            "verify you are human",
            "recaptcha",
            "h-captcha",
            "security check",
            "are you a robot",
            "unusual traffic",
            "too many requests",
            "access denied",
            "blocked",
            "challenge",
            "robot check",
            "human verification",
            "i'm not a robot",
            "please confirm you are human",
            "verify your identity"
    );

    private static final List<String> BLOCK_INDICATORS = Arrays.asList(
            "access denied",
            "blocked",
            "forbidden",
            "403 forbidden",
            "too many requests",
            "429",
            "rate limit exceeded",
            "your request has been blocked"
    );

    /**
     * Check if HTML contains CAPTCHA indicators
     * Maps to Python: captcha_detector.py - has_captcha(html)
     *
     * Python equivalent:
     * def has_captcha(html):
     *     if not html:
     *         return False
     *     triggers = ["captcha", "verify you are human", "recaptcha"]
     *     html_lower = html.lower()
     *     return any(t in html_lower for t in triggers)
     */
    public boolean hasCaptcha(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        String htmlLower = html.toLowerCase();

        for (String trigger : CAPTCHA_TRIGGERS) {
            if (htmlLower.contains(trigger)) {
                log.debug("CAPTCHA detected! Trigger: '{}'", trigger);
                return true;
            }
        }

        return false;
    }

    /**
     * Get the type of CAPTCHA detected
     */
    public String getCaptchaType(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        String htmlLower = html.toLowerCase();

        if (htmlLower.contains("recaptcha")) {
            return "reCAPTCHA";
        } else if (htmlLower.contains("h-captcha")) {
            return "hCaptcha";
        } else if (htmlLower.contains("captcha")) {
            return "Generic CAPTCHA";
        } else if (htmlLower.contains("verify you are human")) {
            return "Verification Challenge";
        } else if (htmlLower.contains("unusual traffic")) {
            return "Traffic Verification";
        } else if (htmlLower.contains("cloudflare")) {
            return "Cloudflare Challenge";
        }

        return null;
    }

    /**
     * Check if the response indicates being blocked
     */
    public boolean isBlocked(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        String htmlLower = html.toLowerCase();

        for (String indicator : BLOCK_INDICATORS) {
            if (htmlLower.contains(indicator)) {
                log.debug("Blocked detected! Indicator: '{}'", indicator);
                return true;
            }
        }

        return false;
    }

    /**
     * Check if the response indicates rate limiting
     */
    public boolean isRateLimited(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }

        String htmlLower = html.toLowerCase();

        return htmlLower.contains("too many requests") ||
                htmlLower.contains("rate limit") ||
                htmlLower.contains("429") ||
                htmlLower.contains("rate limited");
    }
}