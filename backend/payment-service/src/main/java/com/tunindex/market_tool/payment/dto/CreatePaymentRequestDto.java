package com.tunindex.market_tool.payment.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreatePaymentRequestDto {
    private Long userId;
    private Long planId;
    private BigDecimal amount;
    private String currency;
    private String billingPeriod;
    private String customerEmail;
    private String customerName;
    private String customerPhone;
    private String couponCode;
}