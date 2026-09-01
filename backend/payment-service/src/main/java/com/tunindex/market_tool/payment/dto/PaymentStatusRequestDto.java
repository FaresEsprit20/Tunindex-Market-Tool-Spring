package com.tunindex.market_tool.payment.dto;

import lombok.Data;

@Data
public class PaymentStatusRequestDto {
    private String transactionId;
    private String providerPaymentId;
}