package com.tunindex.market_tool.payment.entities;

import com.tunindex.market_tool.payment.entities.enums.DiscountType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promo_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    private String description;

    // Discount type: PERCENTAGE or FIXED
    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    private BigDecimal discountValue;

    @Column(nullable = false)
    private String currency;

    private BigDecimal minPurchaseAmount;

    private BigDecimal maxDiscountAmount;

    private Integer usageLimit;

    private Integer usedCount;

    private Boolean isActive;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    // Which plans this promo applies to (null = all plans)
    private String applicablePlanIds; // Comma-separated IDs

    // First time users only
    private Boolean firstTimeOnly;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        isActive = true;
        usedCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        return isActive != null && isActive &&
                (validFrom == null || now.isAfter(validFrom)) &&
                (validUntil == null || now.isBefore(validUntil)) &&
                (usageLimit == null || usedCount < usageLimit);
    }
}