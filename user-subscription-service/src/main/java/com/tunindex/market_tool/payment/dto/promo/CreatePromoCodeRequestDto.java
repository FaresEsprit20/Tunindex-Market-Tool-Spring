package com.tunindex.market_tool.payment.dto.promo;

import com.tunindex.market_tool.payment.entities.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreatePromoCodeRequestDto {

    @NotBlank(message = "Promo code is required")
    private String code;

    private String description;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be positive")
    private BigDecimal discountValue;

    @NotBlank(message = "Currency is required")
    private String currency;

    private BigDecimal minPurchaseAmount;
    private BigDecimal maxDiscountAmount;
    private Integer usageLimit;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String applicablePlanIds;
    private Boolean firstTimeOnly;

}