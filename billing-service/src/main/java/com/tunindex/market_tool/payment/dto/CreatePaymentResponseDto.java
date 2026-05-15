package com.tunindex.market_tool.payment.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class CreatePaymentResponseDto {
    private String transactionId;
    private String providerPaymentId;
    private String paymentUrl;
    private String status;
    private BigDecimal amount;
    private String currency;
}