package com.tunindex.market_tool.payment.dto;

import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponseDto {
    private Long id;
    private Long transactionId;
    private BigDecimal amount;
    private String reason;
    private RefundStatus status;
    private String providerRefundId;
    private String failureReason;
    private LocalDateTime refundDate;
    private LocalDateTime createdAt;
}