package com.tunindex.market_tool.payment.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RefundPaymentRequestDto {
    private String transactionId;
    private String providerPaymentId;
    private BigDecimal amount;
    private String reason;
}