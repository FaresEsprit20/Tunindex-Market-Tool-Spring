package com.tunindex.market_tool.payment.dto.promo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplyPromoCodeRequestDto {

    @NotBlank(message = "Promo code is required")
    private String code;

    @NotNull(message = "Plan ID is required")
    @Positive(message = "Plan ID must be positive")
    private Long planId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;

}