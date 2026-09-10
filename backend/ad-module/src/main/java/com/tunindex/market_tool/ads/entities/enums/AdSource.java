package com.tunindex.market_tool.ads.entities.enums;

import lombok.Getter;

/**
 * Who supplies the ad and, consequently, who pays.
 *
 * <p>{@code networkManaged} is the field that matters operationally. A
 * network-served ad is rendered by the provider's own script, so what it shows,
 * whether it can be skipped, and what it pays are decided by the network - this
 * module can record and report on it but cannot dictate its behaviour. A direct
 * or house ad is ours end to end.
 *
 * <p>That distinction is worth encoding because the networks enforce their own
 * policies on how their units may be displayed, and an implementation that
 * overrides them tends to end in a suspended account rather than more revenue.
 */
@Getter
public enum AdSource {

    /** Google's self-serve network. Serving rules are AdSense's, not ours. */
    GOOGLE_ADSENSE("Google AdSense", true),

    /** Google's publisher-side ad server, for direct and programmatic deals. */
    GOOGLE_AD_MANAGER("Google Ad Manager", true),

    /** Embedded YouTube video carrying its own monetisation. */
    YOUTUBE("YouTube", true),

    /** Real-time bidding through a partner exchange. */
    PROGRAMMATIC_EXCHANGE("Programmatic exchange", true),

    /** Sold by us to an advertiser directly; terms are whatever was agreed. */
    DIRECT_ADVERTISER("Direct advertiser", false),

    /** Commission on referred conversions rather than on views. */
    AFFILIATE("Affiliate partner", false),

    /** Our own promotion. Earns nothing and is excluded from revenue. */
    HOUSE("House ad", false);

    private final String displayName;

    /**
     * True when the provider serves and controls the creative.
     *
     * <p>Revenue for these is reported by the network and reconciled against
     * our own counts, rather than calculated from them - the two rarely agree
     * exactly, and the network's figure is the one that gets paid.
     */
    private final boolean networkManaged;

    AdSource(String displayName, boolean networkManaged) {
        this.displayName = displayName;
        this.networkManaged = networkManaged;
    }

    /** House ads promote us, so they never contribute to earnings. */
    public boolean earnsRevenue() {
        return this != HOUSE;
    }
}
