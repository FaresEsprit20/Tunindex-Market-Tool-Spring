package com.tunindex.market_tool.payment.service.gateway;


import com.tunindex.market_tool.payment.dto.gateway.*;

public interface PaymentGateway {

    String getProviderName();

    PaymentGatewayResponse createPayment(PaymentGatewayRequest request);

    PaymentGatewayStatusResponse getPaymentStatus(PaymentGatewayStatusRequest request);

    PaymentGatewayStatusResponse processWebhook(PaymentGatewayWebhookPayload payload, String signature);

    PaymentGatewayResponse refundPayment(String providerPaymentId, java.math.BigDecimal amount, String reason);
}