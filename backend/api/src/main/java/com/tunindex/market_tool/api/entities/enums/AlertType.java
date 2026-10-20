package com.tunindex.market_tool.api.entities.enums;

import lombok.Getter;

/**
 * What a rule watches for. Each type reads a figure this platform already
 * computes from real data, so an alert never fires on anything the user
 * couldn't go and verify on the stock's own page.
 */
@Getter
public enum AlertType {

    PRICE_ABOVE("Price rises above", true),
    PRICE_BELOW("Price falls below", true),
    /** Day move in percent, in either direction, exceeds the threshold. */
    DAY_MOVE_EXCEEDS("Daily move exceeds", true),
    /** Tunindex Score crosses up through the threshold. */
    SCORE_ABOVE("Tunidex Score rises above", true),
    SCORE_BELOW("Tunidex Score falls below", true),
    /** Verdict changes at all, e.g. BUY -> STRONG_BUY. No threshold. */
    VERDICT_CHANGE("Verdict changes", false),
    /** A newly-scraped headline classified NEGATIVE. No threshold. */
    NEGATIVE_NEWS("Negative headline published", false),
    /** Position in the 52-week range reaches the threshold (100 = at the low). */
    NEAR_52W_LOW("Approaches its 52-week low", true);

    private final String description;

    /** False for event-style types, where a threshold would be meaningless. */
    private final boolean requiresThreshold;

    AlertType(String description, boolean requiresThreshold) {
        this.description = description;
        this.requiresThreshold = requiresThreshold;
    }
}
