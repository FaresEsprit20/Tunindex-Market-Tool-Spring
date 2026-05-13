package com.tunindex.market_tool.payment.dto;

import com.tunindex.market_tool.payment.entities.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentRequestDto {
    @NotNull
    private Long userId;

    @NotNull
    private Long planId;

    @NotNull
    private PaymentMethod paymentMethod;

    private String billingPeriod; // MONTHLY or YEARLY

    private String couponCode;
}