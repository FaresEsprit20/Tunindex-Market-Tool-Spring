package com.tunindex.market_tool.ads.entities.enums;

import lombok.Getter;

/**
 * Where on the site a slot lives.
 *
 * <p>Placement is kept apart from {@link AdType} because it answers a
 * different question: the type says what the unit is, the placement says where
 * it goes. A banner can sit in a sidebar or above the fold, and those are
 * worth different amounts.
 */
@Getter
public enum AdPlacement {

    DASHBOARD_TOP("Dashboard — above the fold"),
    DASHBOARD_INLINE("Dashboard — between sections"),
    STOCK_LIST_INLINE("Stock list — between rows"),
    STOCK_DETAIL_SIDEBAR("Stock detail — sidebar"),
    PORTFOLIO_SIDEBAR("Portfolio — sidebar"),
    ANALYSIS_PRE_CONTENT("Analysis — before the content"),
    NEWS_FEED_INLINE("News feed — between items"),
    GLOBAL_FOOTER("Site footer"),
    INTERSTITIAL_ON_NAVIGATION("Between page transitions");

    private final String displayName;

    AdPlacement(String displayName) {
        this.displayName = displayName;
    }
}
