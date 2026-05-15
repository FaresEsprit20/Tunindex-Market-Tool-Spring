package com.tunindex.market_tool.payment.dto;

import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentStatusResponseDto {
    private String transactionId;
    private String providerPaymentId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private String failureReason;
}