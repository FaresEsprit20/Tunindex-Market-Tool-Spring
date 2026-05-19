package com.tunindex.market_tool.payment.unit;

import com.tunindex.market_tool.payment.config.KonnectConfig;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayRequest;
import com.tunindex.market_tool.payment.service.gateway.konnect.KonnectPaymentGateway;
import com.tunindex.market_tool.payment.service.payment_transaction.PaymentTransactionService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Integration test - requires Konnect API access")
class KonnectPaymentGatewayIntegrationTest {

    @Autowired
    private KonnectPaymentGateway konnectPaymentGateway;

    @Autowired
    private KonnectConfig konnectConfig;

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    @Test
    @Disabled("Requires actual Konnect API credentials")
    void createPayment_IntegrationTest() {
        // This test requires actual Konnect API credentials
        PaymentGatewayRequest request = PaymentGatewayRequest.builder()
                .transactionId("INT-TXN-001")
                .userId(1L)
                .planId(100L)
                .amount(new BigDecimal("10.00"))
                .currency("TND")
                .customerEmail("test@example.com")
                .customerName("Test User")
                .successUrl("http://localhost:4200/success")
                .cancelUrl("http://localhost:4200/cancel")
                .webhookUrl("http://localhost:8087/internal/payments/webhook/konnect")
                .metadata(Map.of("billingPeriod", "MONTHLY"))
                .build();

        var response = konnectPaymentGateway.createPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getProviderPaymentId()).isNotBlank();
        assertThat(response.getPaymentUrl()).isNotBlank();
    }
}