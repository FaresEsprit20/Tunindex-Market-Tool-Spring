package com.tunindex.market_tool.payment.unit;

import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.payment.client.EmailServiceClient;
import com.tunindex.market_tool.payment.config.KonnectConfig;
import com.tunindex.market_tool.payment.dto.PaymentRequestDto;
import com.tunindex.market_tool.payment.dto.PaymentResponseDto;
import com.tunindex.market_tool.payment.dto.gateway.*;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import com.tunindex.market_tool.payment.service.gateway.konnect.KonnectPaymentGateway;
import com.tunindex.market_tool.payment.service.payment_transaction.PaymentTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KonnectPaymentGateway Unit Tests")
class KonnectPaymentGatewayTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private KonnectConfig konnectConfig;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @Mock
    private EmailServiceClient emailServiceClient;

    @InjectMocks
    private KonnectPaymentGateway konnectPaymentGateway;

    private PaymentGatewayRequest paymentGatewayRequest;
    private PaymentGatewayStatusRequest statusRequest;
    private PaymentGatewayWebhookPayload webhookPayload;

    @BeforeEach
    void setUp() {
        paymentGatewayRequest = PaymentGatewayRequest.builder()
                .transactionId("TXN-001")
                .userId(100L)
                .planId(500L)
                .amount(new BigDecimal("99.99"))
                .currency("TND")
                .customerEmail("test@example.com")
                .customerName("John Doe")
                .customerPhone("+21612345678")
                .successUrl("https://example.com/success")
                .cancelUrl("https://example.com/cancel")
                .webhookUrl("https://example.com/webhook")
                .build();

        statusRequest = PaymentGatewayStatusRequest.builder()
                .providerPaymentId("KONNECT-123")
                .transactionId("TXN-001")
                .build();

        webhookPayload = PaymentGatewayWebhookPayload.builder()
                .eventType("payment.completed")
                .transactionId("TXN-001")
                .providerPaymentId("KONNECT-123")
                .amount(new BigDecimal("99.99"))
                .currency("TND")
                .status("completed")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("createPayment Tests")
    class CreatePaymentTests {

        @Test
        @DisplayName("Should successfully create payment")
        void shouldSuccessfullyCreatePayment() {
            // Given
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", true);
            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("id", "KONNECT-123");
            paymentData.put("payment_url", "https://konnect.com/pay/123");
            konnectResponse.put("data", paymentData);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(eq("X-API-Key"), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any(Map.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            PaymentResponseDto paymentResponse = PaymentResponseDto.builder()
                    .transactionId(1L)
                    .transactionReference("TXN-001")
                    .status(PaymentStatus.PENDING)
                    .build();
            when(paymentTransactionService.initiatePayment(any(PaymentRequestDto.class)))
                    .thenReturn(paymentResponse);

            // When
            PaymentGatewayResponse result = konnectPaymentGateway.createPayment(paymentGatewayRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProviderPaymentId()).isEqualTo("KONNECT-123");
            assertThat(result.getPaymentUrl()).isEqualTo("https://konnect.com/pay/123");
            assertThat(result.getStatus()).isEqualTo("PENDING");
            assertThat(result.getTransactionId()).isEqualTo("TXN-001");

            verify(paymentTransactionService).initiatePayment(any(PaymentRequestDto.class));
            verify(webClient).post();
        }

        @Test
        @DisplayName("Should throw exception when initiatePayment fails")
        void shouldThrowExceptionWhenInitiatePaymentFails() {
            // Given
            when(paymentTransactionService.initiatePayment(any(PaymentRequestDto.class)))
                    .thenThrow(new RuntimeException("Invalid payment transaction"));

            // When & Then
            assertThatThrownBy(() -> konnectPaymentGateway.createPayment(paymentGatewayRequest))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("Failed to create payment: Invalid payment transaction");
        }

        @Test
        @DisplayName("Should throw exception when Konnect API returns error")
        void shouldThrowExceptionWhenKonnectApiReturnsError() {
            // Given
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", false);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(eq("X-API-Key"), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any(Map.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            PaymentResponseDto paymentResponse = PaymentResponseDto.builder()
                    .transactionId(1L)
                    .transactionReference("TXN-001")
                    .status(PaymentStatus.PENDING)
                    .build();
            when(paymentTransactionService.initiatePayment(any(PaymentRequestDto.class)))
                    .thenReturn(paymentResponse);

            // When & Then
            assertThatThrownBy(() -> konnectPaymentGateway.createPayment(paymentGatewayRequest))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("Konnect API error");
        }
    }

    @Nested
    @DisplayName("getPaymentStatus Tests")
    class GetPaymentStatusTests {

        @Test
        @DisplayName("Should successfully get payment status with BANK_CARD")
        void shouldSuccessfullyGetPaymentStatusWithBankCard() {
            // Given
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", true);
            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("status", "completed");
            paymentData.put("amount", 99.99);
            paymentData.put("currency", "TND");
            paymentData.put("payment_method", "BANK_CARD");  // Use enum name, not konnectValue
            konnectResponse.put("data", paymentData);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(eq("X-API-Key"), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            // When
            PaymentGatewayStatusResponse result = konnectPaymentGateway.getPaymentStatus(statusRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getProviderPaymentId()).isEqualTo("KONNECT-123");
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.getAmount()).isEqualByComparingTo("99.99");
        }

        @Test
        @DisplayName("Should successfully get payment status with FLOUCI")
        void shouldSuccessfullyGetPaymentStatusWithFlouci() {
            // Given
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", true);
            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("status", "completed");
            paymentData.put("amount", 99.99);
            paymentData.put("currency", "TND");
            paymentData.put("payment_method", "FLOUCI");  // Use enum name
            konnectResponse.put("data", paymentData);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(eq("X-API-Key"), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            // When
            PaymentGatewayStatusResponse result = konnectPaymentGateway.getPaymentStatus(statusRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should successfully get payment status with WALLET")
        void shouldSuccessfullyGetPaymentStatusWithWallet() {
            // Given
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", true);
            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("status", "completed");
            paymentData.put("amount", 99.99);
            paymentData.put("currency", "TND");
            paymentData.put("payment_method", "WALLET");  // Use enum name
            konnectResponse.put("data", paymentData);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(eq("X-API-Key"), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            // When
            PaymentGatewayStatusResponse result = konnectPaymentGateway.getPaymentStatus(statusRequest);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should throw exception when payment not found")
        void shouldThrowExceptionWhenPaymentNotFound() {
            // Given
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", false);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.get()).thenReturn(requestHeadersUriSpec);
            when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.header(eq("X-API-Key"), anyString())).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            // When & Then
            assertThatThrownBy(() -> konnectPaymentGateway.getPaymentStatus(statusRequest))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("Failed to get payment status");
        }
    }

    @Nested
    @DisplayName("processWebhook Tests")
    class ProcessWebhookTests {

        @Test
        @DisplayName("Should throw exception when signature verification fails")
        void shouldThrowExceptionWhenSignatureVerificationFails() {
            // Given
            when(konnectConfig.getWebhookSecret()).thenReturn("test-secret");
            String invalidSignature = "invalid-signature";

            // When & Then
            assertThatThrownBy(() -> konnectPaymentGateway.processWebhook(webhookPayload, invalidSignature))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("Invalid webhook signature");
        }

        @Test
        @DisplayName("Should handle webhook when no secret configured - returns response")
        void shouldHandleWebhookWhenNoSecretConfigured() {
            // Given
            when(konnectConfig.getWebhookSecret()).thenReturn(null);

            // When
            PaymentGatewayStatusResponse result = konnectPaymentGateway.processWebhook(webhookPayload, "any-signature");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTransactionId()).isEqualTo("TXN-001");
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should process failed payment webhook")
        void shouldProcessFailedPaymentWebhook() {
            // Given
            webhookPayload.setStatus("failed");
            when(konnectConfig.getWebhookSecret()).thenReturn(null);

            PaymentResponseDto paymentResponse = PaymentResponseDto.builder()
                    .transactionId(1L)
                    .transactionReference("TXN-001")
                    .status(PaymentStatus.FAILED)
                    .build();
            when(paymentTransactionService.markAsFailed(eq("TXN-001"), anyString()))
                    .thenReturn(paymentResponse);

            // When
            PaymentGatewayStatusResponse result = konnectPaymentGateway.processWebhook(webhookPayload, "any-signature");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            verify(paymentTransactionService).markAsFailed(eq("TXN-001"), anyString());
        }

        @Test
        @DisplayName("Should handle successful payment webhook when metadata exists")
        void shouldHandleSuccessfulPaymentWebhookWithMetadata() {
            // Given - First create a payment to populate metadata
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", true);
            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("id", "KONNECT-123");
            paymentData.put("payment_url", "https://konnect.com/pay/123");
            konnectResponse.put("data", paymentData);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(eq("X-API-Key"), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any(Map.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            PaymentResponseDto paymentResponse = PaymentResponseDto.builder()
                    .transactionId(1L)
                    .transactionReference("TXN-001")
                    .status(PaymentStatus.PENDING)
                    .build();
            when(paymentTransactionService.initiatePayment(any(PaymentRequestDto.class)))
                    .thenReturn(paymentResponse);

            // Create payment to populate metadata
            konnectPaymentGateway.createPayment(paymentGatewayRequest);

            // Now process webhook
            when(konnectConfig.getWebhookSecret()).thenReturn(null);
            PaymentResponseDto completedResponse = PaymentResponseDto.builder()
                    .transactionId(1L)
                    .transactionReference("TXN-001")
                    .status(PaymentStatus.COMPLETED)
                    .build();
            when(paymentTransactionService.markAsCompleted("TXN-001")).thenReturn(completedResponse);
            doNothing().when(emailServiceClient).sendPaymentConfirmationEmail(anyString(), anyString(), anyString(), anyString(), anyString());

            // When
            PaymentGatewayStatusResponse result = konnectPaymentGateway.processWebhook(webhookPayload, "any-signature");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(paymentTransactionService).markAsCompleted("TXN-001");
            verify(emailServiceClient).sendPaymentConfirmationEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("refundPayment Tests")
    class RefundPaymentTests {

        @Test
        @DisplayName("Should successfully process refund")
        void shouldSuccessfullyProcessRefund() {
            // Given
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", true);
            Map<String, Object> refundData = new HashMap<>();
            refundData.put("order_id", "TXN-001");
            konnectResponse.put("data", refundData);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(eq("X-API-Key"), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any(Map.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            PaymentResponseDto paymentResponse = PaymentResponseDto.builder()
                    .transactionId(1L)
                    .transactionReference("TXN-001")
                    .status(PaymentStatus.REFUNDED)
                    .build();
            when(paymentTransactionService.markAsRefunded("TXN-001")).thenReturn(paymentResponse);

            // When
            PaymentGatewayResponse result = konnectPaymentGateway.refundPayment(
                    "KONNECT-123", new BigDecimal("99.99"), "Customer request"
            );

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("REFUNDED");
            verify(paymentTransactionService).markAsRefunded("TXN-001");
        }

        @Test
        @DisplayName("Should throw exception when refund fails")
        void shouldThrowExceptionWhenRefundFails() {
            // Given
            when(webClientBuilder.build()).thenReturn(webClient);

            Map<String, Object> konnectResponse = new HashMap<>();
            konnectResponse.put("success", false);

            when(konnectConfig.getApiUrl()).thenReturn("https://api.konnect.com");
            when(konnectConfig.getApiKey()).thenReturn("test-api-key");

            when(webClient.post()).thenReturn(requestBodyUriSpec);
            when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.header(eq("X-API-Key"), anyString())).thenReturn(requestBodySpec);
            when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
            when(requestBodySpec.bodyValue(any(Map.class))).thenReturn(requestHeadersSpec);
            when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
            when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(konnectResponse));

            // When & Then
            assertThatThrownBy(() -> konnectPaymentGateway.refundPayment(
                    "KONNECT-123", new BigDecimal("99.99"), "Customer request"
            )).isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("Refund failed");
        }
    }

    @Nested
    @DisplayName("getProviderName Tests")
    class GetProviderNameTests {

        @Test
        @DisplayName("Should return provider name")
        void shouldReturnProviderName() {
            // When
            String providerName = konnectPaymentGateway.getProviderName();

            // Then
            assertThat(providerName).isEqualTo("KONNECT");
        }
    }

}