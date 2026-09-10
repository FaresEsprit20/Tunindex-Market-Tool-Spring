package com.tunindex.market_tool.ads.entities;

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
 * A single thing that happened to an ad: it was shown, watched, or clicked.
 *
 * <p>Stored per event rather than as running counters on the ad, because a
 * counter cannot answer the questions that matter later - which day earned
 * what, whether views are completing, whether one placement outperforms
 * another. Counters also cannot be reconciled against a network's own report
 * when the two disagree, and they will.
 *
 * <p>{@code revenue} is written at the moment the event is recorded, using the
 * rate in force then. Recomputing it later from the ad's current rate would
 * silently rewrite history every time a rate is renegotiated.
 */
@Entity
@Table(name = "ad_events", indexes = {
        @Index(name = "idx_event_ad_time", columnList = "advertisement_id, occurred_at"),
        @Index(name = "idx_event_type_time", columnList = "event_type, occurred_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdEvent {

    /** What happened. Distinct from the billable event, which depends on the model. */
    public enum EventType {
        /** The unit was rendered. */
        IMPRESSION,
        /** A video ran to the end. */
        COMPLETED_VIEW,
        /** A video was abandoned before the end; earns nothing on CPV. */
        SKIPPED,
        /** The viewer clicked through. */
        CLICK,
        /** The advertiser confirmed an action at their end. */
        CONVERSION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "advertisement_id", nullable = false)
    private Long advertisementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /**
     * Who saw it, when known.
     *
     * <p>Nullable on purpose: ads are shown to signed-out visitors too, and an
     * anonymous impression is still a real one. Storing a user id only when we
     * genuinely have it keeps the absence honest rather than filling it with a
     * placeholder that later reads as a real account.
     */
    private Long userId;

    /**
     * How much of a video was watched, as a percentage.
     *
     * <p>Kept because "shown" and "watched" are different things, and on a
     * per-view rate only the second one pays.
     */
    private Integer watchedPercent;

    /** The pricing model in force when this event was recorded. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PricingModel pricingModel;

    /**
     * What this event earned, at the rate that applied when it happened.
     *
     * <p>Zero for a non-billable event - an impression under a CPC deal earns
     * nothing, and recording zero says that plainly, where null would mean
     * "not known".
     */
    @Column(precision = 12, scale = 6)
    private BigDecimal revenue;

    @Column(length = 3)
    private String currency;

    /** Coarse context for reporting; deliberately not a full user agent. */
    @Column(length = 60)
    private String deviceCategory;
}
