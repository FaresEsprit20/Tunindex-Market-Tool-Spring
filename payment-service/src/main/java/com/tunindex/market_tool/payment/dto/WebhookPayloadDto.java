package com.tunindex.market_tool.payment.dto;

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
public class WebhookPayloadDto {
    private String eventType;
    private String transactionId;
    private String providerPaymentId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String signature;
    private LocalDateTime timestamp;
}