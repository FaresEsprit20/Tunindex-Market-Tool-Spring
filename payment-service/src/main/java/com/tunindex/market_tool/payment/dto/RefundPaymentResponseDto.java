package com.tunindex.market_tool.payment.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RefundPaymentResponseDto {
    private String refundId;
    private String transactionId;
    private String providerRefundId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime refundDate;
}