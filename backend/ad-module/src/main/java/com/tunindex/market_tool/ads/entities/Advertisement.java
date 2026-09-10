package com.tunindex.market_tool.ads.entities;

import com.tunindex.market_tool.ads.entities.enums.AdPlacement;
import com.tunindex.market_tool.ads.entities.enums.AdSource;
import com.tunindex.market_tool.ads.entities.enums.AdStatus;
import com.tunindex.market_tool.ads.entities.enums.AdType;
import com.tunindex.market_tool.ads.entities.enums.PricingModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One ad the site can serve.
 *
 * <p>Holds three things that are easy to conflate: what the unit <em>is</em>
 * (type and source), where it <em>goes</em> (placement), and what it
 * <em>earns</em> (pricing model and rate). They vary independently - a video
 * pre-roll can come from YouTube or a direct advertiser, sit in two different
 * slots, and be billed per view or as a flat sponsorship.
 *
 * <p><b>On skipping.</b> {@code skippable} and {@code minimumDisplaySeconds}
 * describe the unit's intended behaviour, and are honoured for ads we serve
 * ourselves. For a network-served unit they are a record rather than a
 * control: AdSense, Ad Manager and YouTube render through their own scripts
 * and enforce their own policies, several of which prohibit publishers from
 * suppressing skip controls or blocking interaction. Those policies are worth
 * respecting for a mercenary reason as much as any other - a suspended
 * publisher account pays nothing at all.
 */
@Entity
@Table(name = "advertisements", indexes = {
        @Index(name = "idx_ad_status_placement", columnList = "status, placement"),
        @Index(name = "idx_ad_window", columnList = "starts_at, ends_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Advertisement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Internal label, shown in the admin list rather than to viewers. */
    @Column(nullable = false, length = 160)
    private String name;

    /** Who is paying. Free text: not every advertiser is a system account. */
    @Column(length = 160)
    private String advertiser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AdPlacement placement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdStatus status;

    // ── Creative ──────────────────────────────────────────────────────────

    /** Image, video or embed URL. Null for a network unit that self-renders. */
    @Column(length = 1000)
    private String creativeUrl;

    /** Where a click goes. Null when the network owns the click. */
    @Column(length = 1000)
    private String targetUrl;

    /**
     * The provider's own slot identifier - an AdSense ad-unit id, an Ad
     * Manager slot path, a YouTube video id.
     *
     * <p>Required for network-served units, because the provider's script
     * needs it to know what to render.
     */
    @Column(length = 200)
    private String externalUnitId;

    @Column(length = 500)
    private String altText;

    // ── Behaviour ─────────────────────────────────────────────────────────

    /**
     * Whether the viewer may dismiss the unit.
     *
     * <p>Enforced by our own player for self-served ads; recorded only for
     * network-served ones, where the provider decides.
     */
    @Column(nullable = false)
    private boolean skippable;

    /**
     * Seconds before a skip control appears, for a skippable unit.
     *
     * <p>Only meaningful when {@code skippable} is true - it is the delay
     * before skipping becomes possible, not a minimum watch time.
     */
    private Integer skipAfterSeconds;

    /** Full length of the creative, for video formats. */
    private Integer durationSeconds;

    // ── Money ─────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PricingModel pricingModel;

    /**
     * The agreed rate, in {@link #currency}.
     *
     * <p>Its meaning depends on the pricing model: per thousand impressions
     * for CPM, per click for CPC, per completed view for CPV, and the whole
     * fee for a flat rate. Stored as a decimal rather than a floating-point
     * number because it is money.
     */
    @Column(precision = 12, scale = 4)
    private BigDecimal rate;

    @Column(length = 3)
    private String currency;

    /**
     * Ceiling on what this ad may earn before it stops serving.
     *
     * <p>Null means uncapped. When set, the ad moves to
     * {@code BUDGET_EXHAUSTED} rather than continuing to accrue - delivering
     * beyond an agreed budget is unbillable work.
     */
    @Column(precision = 14, scale = 4)
    private BigDecimal budgetCap;

    /** Running total, updated as billable events land. */
    @Column(precision = 14, scale = 4)
    private BigDecimal revenueAccrued;

    // ── Scheduling ────────────────────────────────────────────────────────

    private LocalDateTime startsAt;
    private LocalDateTime endsAt;

    /** Higher wins when several ads compete for the same slot. */
    @Column(nullable = false)
    private int priority;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Whether this ad may be served right now.
     *
     * <p>Status alone is not enough: a campaign left ACTIVE past its end date,
     * or one that has spent its budget, must stop. Asking the entity keeps
     * that rule in one place instead of repeated at each call site.
     */
    public boolean isServableNow(LocalDateTime now) {
        if (status == null || !status.isServable()) {
            return false;
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return false;
        }
        if (endsAt != null && now.isAfter(endsAt)) {
            return false;
        }
        return !hasExhaustedBudget();
    }

    public boolean hasExhaustedBudget() {
        return budgetCap != null
                && revenueAccrued != null
                && revenueAccrued.compareTo(budgetCap) >= 0;
    }
}
