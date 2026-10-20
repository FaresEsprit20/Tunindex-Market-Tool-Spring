package com.tunindex.market_tool.api.entities;

import com.tunindex.market_tool.api.entities.enums.AlertType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One user's standing rule: "tell me when SYMBOL does X".
 *
 * <p>Rules are evaluated against real stored figures on a schedule (see
 * AlertEvaluationService), never against a client-supplied value.
 */
@Entity
@Table(name = "alert_rules", indexes = {
        @Index(name = "idx_alert_rules_user", columnList = "user_id"),
        @Index(name = "idx_alert_rules_enabled", columnList = "enabled")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AlertType type;

    /** Null for event-style types — see AlertType.requiresThreshold. */
    @Column(precision = 19, scale = 4)
    private BigDecimal threshold;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * Threshold rules fire on the crossing, not on every evaluation while
     * the condition holds — this records the side the value was on last
     * time so a sustained condition doesn't notify every few minutes.
     */
    @Column(name = "last_observed_value", precision = 19, scale = 4)
    private BigDecimal lastObservedValue;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
