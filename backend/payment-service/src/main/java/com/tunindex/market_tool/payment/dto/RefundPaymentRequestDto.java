package com.tunindex.market_tool.payment.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Builder
@Data
public class RefundPaymentRequestDto {
    private String transactionId;
    private String providerPaymentId;
    private BigDecimal amount;
    private String reason;
}