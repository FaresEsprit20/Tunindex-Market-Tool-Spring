package com.tunindex.market_tool.user_subscription.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "subscription_plans")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal priceMonthly;

    private BigDecimal priceYearly;

    private String currency;

    // Multi-currency support - store prices in different currencies as JSON
    @Convert(converter = PriceMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, BigDecimal> pricesMonthly;

    @Convert(converter = PriceMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, BigDecimal> pricesYearly;

    private Integer durationDays;

    private String features;

    private Integer apiCallsLimit;

    private Boolean isActive;

    private Integer displayOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}