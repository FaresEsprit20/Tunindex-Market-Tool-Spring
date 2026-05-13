package com.tunindex.market_tool.payment.dto.gateway;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentGatewayResponse {
    private String providerPaymentId;
    private String paymentUrl;
    private String status;
    private String transactionId;
}