package com.tunindex.market_tool.ads.entities.enums;

import com.tunindex.market_tool.ads.entities.enums.AdPlacement;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The things a user has to watch an ad to reach.
 *
 * <p>Listed explicitly rather than accepting any string, so a gate exists only
 * where someone decided one should. An open-ended gate name would let a typo
 * in the frontend silently create a paywall on a feature nobody meant to
 * charge for - or, worse, let a caller invent a feature name that no rule
 * covers and walk straight through.
 *
 * <p>Each feature names the placement its ad is drawn from, so the campaign
 * shown at a gate is scheduled like any other inventory instead of being
 * hard-coded here.
 */
@Getter
@RequiredArgsConstructor
public enum GatedFeature {

    /** Kicking off a collection run - expensive, and worth an ad. */
    PIPELINE_RUN(AdPlacement.INTERSTITIAL_ON_NAVIGATION, "Run the data pipeline"),

    /** The full technical and fundamental breakdown for a stock. */
    ADVANCED_ANALYSIS(AdPlacement.ANALYSIS_PRE_CONTENT, "Full stock analysis"),

    /** Correlation and risk figures across the whole portfolio. */
    PORTFOLIO_ANALYTICS(AdPlacement.PORTFOLIO_SIDEBAR, "Portfolio risk analytics"),

    /** Downloading data rather than reading it on screen. */
    DATA_EXPORT(AdPlacement.DASHBOARD_INLINE, "Export data");

    private final AdPlacement placement;
    private final String label;
}
