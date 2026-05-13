package com.tunindex.market_tool.payment.service.gateway.konnect;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.payment.client.ApiServiceClient;
import com.tunindex.market_tool.payment.client.EmailServiceClient;
import com.tunindex.market_tool.payment.config.KonnectConfig;
import com.tunindex.market_tool.payment.dto.PaymentMethodType;
import com.tunindex.market_tool.payment.dto.UserPaymentInfoDto;
import com.tunindex.market_tool.payment.dto.gateway.*;
import com.tunindex.market_tool.payment.service.gateway.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class KonnectPaymentGateway implements PaymentGatewayService {

    private final WebClient.Builder webClientBuilder;
    private final KonnectConfig konnectConfig;
    private final EmailServiceClient emailServiceClient;
    private final ApiServiceClient apiServiceClient;

    @Override
    public String getProviderName() {
        return "KONNECT";
    }

    @Override
    public PaymentGatewayResponse createPayment(PaymentGatewayRequest request) {
        log.info("💰 Creating Konnect payment for transaction: {}", request.getTransactionId());

        try {
            Map<String, Object> konnectRequest = buildKonnectRequest(request);

            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(konnectConfig.getApiUrl() + "/payments")
                    .header("X-API-Key", konnectConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(konnectRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new InvalidOperationException(
                        "Konnect API error: " + response,
                        ErrorCodes.KONNECT_API_ERROR,
                        java.util.List.of("Payment gateway error")
                );
            }

            Map<String, Object> paymentData = (Map<String, Object>) response.get("data");

            return PaymentGatewayResponse.builder()
                    .providerPaymentId((String) paymentData.get("id"))
                    .paymentUrl((String) paymentData.get("payment_url"))
                    .status("PENDING")
                    .transactionId(request.getTransactionId())
                    .build();

        } catch (Exception e) {
            log.error("❌ Konnect payment creation failed: {}", e.getMessage());
            throw new InvalidOperationException(
                    "Failed to create payment: " + e.getMessage(),
                    ErrorCodes.KONNECT_API_ERROR,
                    java.util.List.of(e.getMessage())
            );
        }
    }

    @Override
    public PaymentGatewayStatusResponse getPaymentStatus(PaymentGatewayStatusRequest request) {
        log.info("🔍 Getting Konnect payment status for: {}", request.getProviderPaymentId());

        try {
            Map<String, Object> response = webClientBuilder.build()
                    .get()
                    .uri(konnectConfig.getApiUrl() + "/payments/" + request.getProviderPaymentId())
                    .header("X-API-Key", konnectConfig.getApiKey())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new InvalidOperationException(
                        "Failed to get payment status",
                        ErrorCodes.KONNECT_PAYMENT_NOT_FOUND,
                        java.util.List.of("Payment not found")
                );
            }

            Map<String, Object> paymentData = (Map<String, Object>) response.get("data");
            String status = (String) paymentData.get("status");

            BigDecimal amount = BigDecimal.valueOf(((Number) paymentData.get("amount")).doubleValue());

            return PaymentGatewayStatusResponse.builder()
                    .providerPaymentId(request.getProviderPaymentId())
                    .transactionId(request.getTransactionId())
                    .status(mapKonnectStatus(status))
                    .amount(amount)
                    .currency((String) paymentData.get("currency"))
                    .paymentMethod((String) paymentData.get("payment_method"))
                    .paymentDate(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("❌ Failed to get payment status: {}", e.getMessage());
            throw new InvalidOperationException(
                    "Failed to get payment status: " + e.getMessage(),
                    ErrorCodes.KONNECT_PAYMENT_NOT_FOUND,
                    java.util.List.of(e.getMessage())
            );
        }
    }

    @Override
    public PaymentGatewayStatusResponse processWebhook(PaymentGatewayWebhookPayload payload, String signature) {
        log.info("📨 Processing Konnect webhook for transaction: {}", payload.getTransactionId());

        if (!verifySignature(payload, signature)) {
            throw new InvalidOperationException(
                    "Invalid webhook signature",
                    ErrorCodes.KONNECT_INVALID_SIGNATURE,
                    java.util.List.of("Signature verification failed")
            );
        }

        String status = payload.getStatus();
        boolean isSuccessful = "completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status);

        // Send email notification on successful payment
        if (isSuccessful) {
            try {
                // Fetch user info using transaction ID from metadata or API
                // For now, we'll try to get it from the payload or a separate call
                sendPaymentSuccessEmail(payload);
            } catch (Exception e) {
                log.error("Failed to send payment confirmation email: {}", e.getMessage());
                // Don't throw - email failure shouldn't break payment processing
            }
        }

        return PaymentGatewayStatusResponse.builder()
                .providerPaymentId(payload.getProviderPaymentId())
                .transactionId(payload.getTransactionId())
                .status(mapKonnectStatus(status))
                .amount(payload.getAmount())
                .currency(payload.getCurrency())
                .paymentDate(LocalDateTime.now())
                .failureReason("FAILED".equals(status) ? "Payment failed" : null)
                .build();
    }

    @Override
    public PaymentGatewayResponse refundPayment(String providerPaymentId, BigDecimal amount, String reason) {
        log.info("🔄 Processing Konnect refund for payment: {}", providerPaymentId);

        try {
            Map<String, Object> refundRequest = new HashMap<>();
            refundRequest.put("amount", amount);
            refundRequest.put("reason", reason != null ? reason : "Customer request");

            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(konnectConfig.getApiUrl() + "/payments/" + providerPaymentId + "/refund")
                    .header("X-API-Key", konnectConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(refundRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                throw new InvalidOperationException(
                        "Refund failed",
                        ErrorCodes.KONNECT_REFUND_FAILED,
                        java.util.List.of("Refund processing error")
                );
            }

            Map<String, Object> refundData = (Map<String, Object>) response.get("data");

            // Send refund confirmation email
            try {
                sendRefundConfirmationEmail(providerPaymentId, amount);
            } catch (Exception e) {
                log.error("Failed to send refund confirmation email: {}", e.getMessage());
            }

            return PaymentGatewayResponse.builder()
                    .providerPaymentId(providerPaymentId)
                    .status("REFUNDED")
                    .transactionId((String) refundData.get("order_id"))
                    .build();

        } catch (Exception e) {
            log.error("❌ Refund failed: {}", e.getMessage());
            throw new InvalidOperationException(
                    "Failed to process refund: " + e.getMessage(),
                    ErrorCodes.KONNECT_REFUND_FAILED,
                    java.util.List.of(e.getMessage())
            );
        }
    }

    private Map<String, Object> buildKonnectRequest(PaymentGatewayRequest request) {
        Map<String, Object> konnectRequest = new HashMap<>();
        konnectRequest.put("amount", request.getAmount());
        konnectRequest.put("currency", request.getCurrency());
        konnectRequest.put("order_id", request.getTransactionId());

        Map<String, Object> customer = new HashMap<>();
        customer.put("email", request.getCustomerEmail());
        customer.put("name", request.getCustomerName());
        if (request.getCustomerPhone() != null) {
            customer.put("phone", request.getCustomerPhone());
        }
        konnectRequest.put("customer", customer);

        konnectRequest.put("success_url", request.getSuccessUrl());
        konnectRequest.put("cancel_url", request.getCancelUrl());
        konnectRequest.put("webhook_url", request.getWebhookUrl());

        // Add allowed payment methods from configuration
        if (konnectConfig.getAllowedPaymentMethods() != null && !konnectConfig.getAllowedPaymentMethods().isEmpty()) {
            List<String> allowedMethods = konnectConfig.getAllowedPaymentMethods().stream()
                    .map(PaymentMethodType::getKonnectValue)
                    .collect(Collectors.toList());
            konnectRequest.put("accepted_payment_methods", allowedMethods);
            log.info("Using configured payment methods: {}", allowedMethods);
        }

        if (request.getMetadata() != null) {
            konnectRequest.put("metadata", request.getMetadata());
        }

        return konnectRequest;
    }

    private void sendPaymentSuccessEmail(PaymentGatewayWebhookPayload payload) {
        String email = null;
        String name = "Customer";
        Long userId = null;

        // Try to get user info from metadata or API
        // This is a placeholder - you would need to implement user lookup
        // based on your transaction ID mapping

        if (email != null) {
            emailServiceClient.sendPaymentConfirmationEmail(
                    email,
                    name,
                    payload.getAmount().toString(),
                    payload.getCurrency(),
                    payload.getTransactionId()
            );
            log.info("Payment confirmation email sent to: {}", email);
        }
    }

    private void sendRefundConfirmationEmail(String providerPaymentId, BigDecimal amount) {
        // Implement refund email logic
        log.info("Refund processed for payment: {}, amount: {}", providerPaymentId, amount);
    }

    private String mapKonnectStatus(String konnectStatus) {
        if (konnectStatus == null) return "PENDING";

        switch (konnectStatus.toLowerCase()) {
            case "completed": case "success": case "paid":
                return "COMPLETED";
            case "failed": case "error": case "declined":
                return "FAILED";
            case "refunded":
                return "REFUNDED";
            case "processing":
                return "PROCESSING";
            case "cancelled":
                return "CANCELLED";
            default:
                return "PENDING";
        }
    }

    private boolean verifySignature(PaymentGatewayWebhookPayload payload, String signature) {
        // TODO: Implement actual signature verification using konnectConfig.getWebhookSecret()
        return signature != null && !signature.isEmpty();
    }

}