package com.tunindex.market_tool.payment.dto;

import com.tunindex.market_tool.payment.entities.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDto {
    private Long userId;
    private Long planId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private String billingPeriod; // MONTHLY or YEARLY
    private String couponCode;
    private String successUrl;
    private String cancelUrl;
}