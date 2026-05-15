package com.tunindex.market_tool.payment.dto.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentGatewayStatusRequest {
    private String providerPaymentId;
    private String transactionId;
}