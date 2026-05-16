package com.tunindex.market_tool.user_subscription.entities;

import com.tunindex.market_tool.user_subscription.entities.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @ManyToOne
    @JoinColumn(name = "plan_id")
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    private BillingPeriod billingPeriod;

    private Boolean autoRenew;

    private String cancellationReason;

    private LocalDateTime cancelledAt;

    // New fields for auto-renewal
    private Integer renewalAttempts;
    private LocalDateTime lastRenewalAttempt;
    private String lastRenewalError;
    private Boolean renewalFailed;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        startDate = LocalDateTime.now();
        autoRenew = true;
        renewalAttempts = 0;
        renewalFailed = false;
        if (status == null) status = SubscriptionStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}