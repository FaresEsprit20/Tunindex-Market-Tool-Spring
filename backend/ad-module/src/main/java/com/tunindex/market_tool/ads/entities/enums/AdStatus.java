package com.tunindex.market_tool.ads.entities.enums;

import lombok.Getter;

/**
 * Where an ad sits in its life.
 *
 * <p>Only {@link #ACTIVE} is servable, and that is checked against the date
 * window too - a campaign left ACTIVE past its end date must stop earning, or
 * we would be billing an advertiser for delivery they did not buy.
 */
@Getter
public enum AdStatus {

    /** Being prepared. Never served. */
    DRAFT("Draft", false),

    /** Approved, waiting for its start date. */
    SCHEDULED("Scheduled", false),

    /** Live, subject to its date window and budget. */
    ACTIVE("Active", true),

    /** Temporarily stopped; keeps its stats and can resume. */
    PAUSED("Paused", false),

    /** Ran to its end date. */
    EXPIRED("Expired", false),

    /** Stopped early because its budget was spent. */
    BUDGET_EXHAUSTED("Budget exhausted", false),

    /** Retained for reporting, out of rotation for good. */
    ARCHIVED("Archived", false);

    private final String displayName;
    private final boolean servable;

    AdStatus(String displayName, boolean servable) {
        this.displayName = displayName;
        this.servable = servable;
    }
}
