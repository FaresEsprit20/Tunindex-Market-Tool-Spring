package com.tunindex.market_tool.payment.dto.promo;

import com.tunindex.market_tool.payment.entities.enums.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PromoCodeDto {
    private Long id;
    private String code;
    private String description;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private String currency;
    private BigDecimal minPurchaseAmount;
    private BigDecimal maxDiscountAmount;
    private Integer usageLimit;
    private Integer usedCount;
    private Boolean isActive;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String applicablePlanIds;
    private Boolean firstTimeOnly;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}