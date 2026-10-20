package com.tunindex.market_tool.ads.entities.enums;

import lombok.Getter;

/**
 * The shape an ad takes on the page.
 *
 * <p>Kept separate from {@link AdSource}: the same network can serve several
 * formats, and the same format can come from different networks. Collapsing
 * them into one enum would make "a YouTube banner" and "an AdSense pre-roll"
 * inexpressible.
 *
 * <p>{@code defaultSkippable} records what each format normally allows, not a
 * rule - an individual ad overrides it. It exists so a new ad starts with the
 * behaviour its format usually has rather than a blanket default.
 */
@Getter
public enum AdType {

    /** Video before the content starts. The format most often non-skippable. */
    VIDEO_PREROLL("Pre-roll video", true, false),

    /** Video partway through longer content. */
    VIDEO_MIDROLL("Mid-roll video", true, false),

    /** Video after the content ends; easiest to abandon, so worth least. */
    VIDEO_POSTROLL("Post-roll video", true, true),

    /** Static or animated image in a fixed slot. Nothing to skip. */
    BANNER("Banner", false, false),

    /** Full-screen unit between two views. */
    INTERSTITIAL("Interstitial", false, true),

    /** Styled to match surrounding content, disclosed as sponsored. */
    NATIVE("Native placement", false, true),

    /** Viewer opts in for something in return; always skippable by definition. */
    REWARDED("Rewarded video", true, true),

    /** Text or image block, typically network-served. */
    DISPLAY("Display unit", false, false),

    /** Paid mention inside editorial content. */
    SPONSORED_CONTENT("Sponsored content", false, true);

    private final String displayName;

    /** Whether the format carries video, which decides how views are counted. */
    private final boolean video;

    /** What this format normally permits; individual ads may differ. */
    private final boolean defaultSkippable;

    AdType(String displayName, boolean video, boolean defaultSkippable) {
        this.displayName = displayName;
        this.video = video;
        this.defaultSkippable = defaultSkippable;
    }
}
