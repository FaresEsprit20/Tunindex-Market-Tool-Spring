package com.tunindex.market_tool.payment.dto;

import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
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
public class PaymentResponseDto {
    private Long transactionId;
    private String transactionReference;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String paymentUrl;
    private LocalDateTime createdAt;
}