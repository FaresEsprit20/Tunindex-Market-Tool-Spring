package com.tunindex.market_tool.payment.dto.gateway;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
public class PaymentGatewayRequest {
    private String transactionId;
    private Long userId;
    private Long planId;
    private BigDecimal amount;
    private String currency;
    private String customerEmail;
    private String customerName;
    private String customerPhone;
    private String successUrl;
    private String cancelUrl;
    private String webhookUrl;
    private Map<String, Object> metadata;
}