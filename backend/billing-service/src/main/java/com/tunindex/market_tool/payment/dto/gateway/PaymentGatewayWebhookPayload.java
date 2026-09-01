package com.tunindex.market_tool.payment.dto.gateway;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentGatewayWebhookPayload {
    private String eventType;
    private String transactionId;
    private String providerPaymentId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String signature;
    private LocalDateTime timestamp;
}