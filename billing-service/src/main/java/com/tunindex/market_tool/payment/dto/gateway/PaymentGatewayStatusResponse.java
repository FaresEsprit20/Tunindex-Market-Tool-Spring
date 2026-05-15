package com.tunindex.market_tool.payment.dto.gateway;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentGatewayStatusResponse {
    private String providerPaymentId;
    private String transactionId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private LocalDateTime paymentDate;
    private String failureReason;
}