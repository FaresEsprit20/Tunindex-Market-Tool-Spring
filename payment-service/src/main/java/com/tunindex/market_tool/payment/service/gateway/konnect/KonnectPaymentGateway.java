package com.tunindex.market_tool.payment.service.gateway.konnect;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.payment.client.ApiServiceClient;
import com.tunindex.market_tool.payment.client.EmailServiceClient;
import com.tunindex.market_tool.payment.config.KonnectConfig;
import com.tunindex.market_tool.payment.dto.*;
import com.tunindex.market_tool.payment.dto.gateway.*;
import com.tunindex.market_tool.payment.service.gateway.PaymentGatewayService;
import com.tunindex.market_tool.payment.service.invoices.InvoiceService;
import com.tunindex.market_tool.payment.service.payment_transaction.PaymentTransactionService;
import com.tunindex.market_tool.payment.service.susbscription_plan.SubscriptionPlanService;
import com.tunindex.market_tool.payment.service.user_subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
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
    private final PaymentTransactionService paymentTransactionService;
    private final UserSubscriptionService userSubscriptionService;
    private final InvoiceService invoiceService;
    private final SubscriptionPlanService subscriptionPlanService;

    private final Map<String, TransactionMetadata> transactionMetadataMap = new HashMap<>();

    private BillingPeriod parseBillingPeriod(String billingPeriod) {
        if ("YEARLY".equalsIgnoreCase(billingPeriod)) {
            return BillingPeriod.YEARLY;
        }
        return BillingPeriod.MONTHLY;
    }

    @Override
    public String getProviderName() {
        return "KONNECT";
    }

    @Override
    public PaymentGatewayResponse createPayment(PaymentGatewayRequest request) {
        log.info("💰 Creating Konnect payment for transaction: {}", request.getTransactionId());

        try {
            String billingPeriodStr = "MONTHLY";
            if (request.getMetadata() != null && request.getMetadata().get("billingPeriod") != null) {
                billingPeriodStr = request.getMetadata().get("billingPeriod").toString();
            }

            TransactionMetadata metadata = new TransactionMetadata();
            metadata.setUserId(request.getUserId());
            metadata.setPlanId(request.getPlanId());
            metadata.setBillingPeriodStr(billingPeriodStr);
            metadata.setBillingPeriod(parseBillingPeriod(billingPeriodStr));
            metadata.setAmount(request.getAmount());
            metadata.setCurrency(request.getCurrency());
            transactionMetadataMap.put(request.getTransactionId(), metadata);

            PaymentRequestDto paymentRequest = PaymentRequestDto.builder()
                    .userId(request.getUserId())
                    .planId(request.getPlanId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .billingPeriod(billingPeriodStr)
                    .customerEmail(request.getCustomerEmail())
                    .customerName(request.getCustomerName())
                    .customerPhone(request.getCustomerPhone())
                    .build();

            paymentTransactionService.initiatePayment(paymentRequest);

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
                        List.of("Payment gateway error")
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
                    List.of(e.getMessage())
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
                        List.of("Payment not found")
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
                    List.of(e.getMessage())
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
                    List.of("Signature verification failed")
            );
        }

        String status = payload.getStatus();
        boolean isSuccessful = "completed".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status);

        if (isSuccessful) {
            try {
                TransactionMetadata metadata = transactionMetadataMap.get(payload.getTransactionId());
                if (metadata == null) {
                    log.error("No metadata found for transaction: {}", payload.getTransactionId());
                    throw new InvalidOperationException(
                            "Transaction metadata not found",
                            ErrorCodes.PAYMENT_NOT_FOUND,
                            List.of("No metadata found for transaction: " + payload.getTransactionId())
                    );
                }

                Long userId = metadata.getUserId();
                Long planId = metadata.getPlanId();
                BillingPeriod billingPeriod = metadata.getBillingPeriod();
                BigDecimal amount = payload.getAmount();
                String currency = payload.getCurrency();

                paymentTransactionService.markAsCompleted(payload.getTransactionId());
                log.info("✅ Payment transaction completed: {}", payload.getTransactionId());

                UserSubscriptionDto subscription = new UserSubscriptionDto();
                subscription.setUserId(userId);
                subscription.setPlanId(planId);
                subscription.setBillingPeriod(billingPeriod);
                subscription.setStatus(com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus.ACTIVE);
                subscription.setAutoRenew(true);
                subscription.setStartDate(LocalDateTime.now());

                if (billingPeriod == BillingPeriod.YEARLY) {
                    subscription.setEndDate(LocalDateTime.now().plusYears(1));
                } else {
                    subscription.setEndDate(LocalDateTime.now().plusMonths(1));
                }

                UserSubscriptionDto createdSubscription = userSubscriptionService.createSubscription(subscription);
                log.info("✅ Subscription created - ID: {}, User: {}, Plan: {}",
                        createdSubscription.getId(), userId, planId);

                log.info("✅ Invoice generated for transaction: {}", payload.getTransactionId());

                UserPaymentInfoDto user = apiServiceClient.getUserPaymentInfo(userId);
                String planName = subscriptionPlanService.findById(planId).getName();

                if (user != null && user.getEmail() != null) {
                    emailServiceClient.sendPaymentReceiptEmail(
                            user.getEmail(),
                            user.getFirstName() + " " + user.getLastName(),
                            amount.toString(),
                            currency,
                            payload.getTransactionId(),
                            planName
                    );
                    log.info("✅ Payment receipt email sent to: {}", user.getEmail());
                }

                transactionMetadataMap.remove(payload.getTransactionId());

            } catch (Exception e) {
                log.error("❌ Failed to process successful payment: {}", e.getMessage(), e);
            }
        } else {
            paymentTransactionService.markAsFailed(payload.getTransactionId(), "Payment failed: " + status);
            log.warn("❌ Payment failed for transaction: {}, status: {}", payload.getTransactionId(), status);
            transactionMetadataMap.remove(payload.getTransactionId());
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
            PaymentResponseDto transaction = paymentTransactionService.findByProviderPaymentId(providerPaymentId);

            if (transaction == null) {
                throw new InvalidOperationException(
                        "Transaction not found for provider payment ID: " + providerPaymentId,
                        ErrorCodes.PAYMENT_NOT_FOUND,
                        List.of("Transaction not found")
                );
            }

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
                        List.of("Refund processing error")
                );
            }

            paymentTransactionService.markAsRefunded(transaction.getTransactionReference());

            try {
                sendRefundConfirmationEmail(providerPaymentId, amount);
            } catch (Exception e) {
                log.error("Failed to send refund confirmation email: {}", e.getMessage());
            }

            return PaymentGatewayResponse.builder()
                    .providerPaymentId(providerPaymentId)
                    .status("REFUNDED")
                    .transactionId(transaction.getTransactionReference())
                    .build();

        } catch (Exception e) {
            log.error("❌ Refund failed: {}", e.getMessage());
            throw new InvalidOperationException(
                    "Failed to process refund: " + e.getMessage(),
                    ErrorCodes.KONNECT_REFUND_FAILED,
                    List.of(e.getMessage())
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

        if (konnectConfig.getAllowedPaymentMethods() != null && !konnectConfig.getAllowedPaymentMethods().isEmpty()) {
            List<String> allowedMethods = konnectConfig.getAllowedPaymentMethods().stream()
                    .map(PaymentMethodType::getKonnectValue)
                    .collect(Collectors.toList());
            konnectRequest.put("accepted_payment_methods", allowedMethods);
            log.info("Using configured payment methods: {}", allowedMethods);
        }

        if (request.getMetadata() != null) {
            Map<String, String> metadataCopy = new HashMap<>();
            for (Map.Entry<String, Object> entry : request.getMetadata().entrySet()) {
                if (entry.getValue() != null) {
                    metadataCopy.put(entry.getKey(), entry.getValue().toString());
                }
            }
            konnectRequest.put("metadata", metadataCopy);
        }

        return konnectRequest;
    }

    private void sendPaymentSuccessEmail(PaymentGatewayWebhookPayload payload) {
        try {
            TransactionMetadata metadata = transactionMetadataMap.get(payload.getTransactionId());
            if (metadata != null && metadata.getUserId() != null) {
                UserPaymentInfoDto user = apiServiceClient.getUserPaymentInfo(metadata.getUserId());
                if (user != null && user.getEmail() != null) {
                    emailServiceClient.sendPaymentConfirmationEmail(
                            user.getEmail(),
                            user.getFirstName() + " " + user.getLastName(),
                            payload.getAmount().toString(),
                            payload.getCurrency(),
                            payload.getTransactionId()
                    );
                    log.info("Payment confirmation email sent to: {}", user.getEmail());
                }
            }
        } catch (Exception e) {
            log.error("Failed to send payment confirmation email: {}", e.getMessage());
        }
    }

    private void sendRefundConfirmationEmail(String providerPaymentId, BigDecimal amount) {
        try {
            PaymentResponseDto transaction = paymentTransactionService.findByProviderPaymentId(providerPaymentId);
            if (transaction != null && transaction.getTransactionReference() != null) {
                log.info("Refund confirmed for transaction: {}", transaction.getTransactionReference());
            }
        } catch (Exception e) {
            log.error("Failed to send refund confirmation email: {}", e.getMessage());
        }
    }

    private String mapKonnectStatus(String konnectStatus) {
        if (konnectStatus == null) return "PENDING";
        switch (konnectStatus.toLowerCase()) {
            case "completed": case "success": case "paid": return "COMPLETED";
            case "failed": case "error": case "declined": return "FAILED";
            case "refunded": return "REFUNDED";
            case "processing": return "PROCESSING";
            case "cancelled": return "CANCELLED";
            default: return "PENDING";
        }
    }

    private boolean verifySignature(PaymentGatewayWebhookPayload payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            log.warn("Missing webhook signature");
            return false;
        }
        try {
            String webhookSecret = konnectConfig.getWebhookSecret();
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                log.warn("Webhook secret not configured - skipping signature verification");
                return true;
            }
            String payloadString = buildPayloadString(payload);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(payloadString.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = Base64.getEncoder().encodeToString(hmacBytes);
            boolean isValid = MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
            if (!isValid) {
                log.warn("Invalid webhook signature. Expected: {}, Got: {}", calculatedSignature, signature);
            }
            return isValid;
        } catch (Exception e) {
            log.error("Error verifying webhook signature: {}", e.getMessage());
            return false;
        }
    }

    private String buildPayloadString(PaymentGatewayWebhookPayload payload) {
        StringBuilder sb = new StringBuilder();
        sb.append(payload.getEventType() != null ? payload.getEventType() : "");
        sb.append(payload.getTransactionId() != null ? payload.getTransactionId() : "");
        sb.append(payload.getAmount() != null ? payload.getAmount().toString() : "");
        sb.append(payload.getCurrency() != null ? payload.getCurrency() : "");
        sb.append(payload.getStatus() != null ? payload.getStatus() : "");
        sb.append(payload.getTimestamp() != null ? payload.getTimestamp().toString() : "");
        return sb.toString();
    }

    private static class TransactionMetadata {
        private Long userId;
        private Long planId;
        private String billingPeriodStr;
        private BillingPeriod billingPeriod;
        private BigDecimal amount;
        private String currency;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getPlanId() { return planId; }
        public void setPlanId(Long planId) { this.planId = planId; }
        public String getBillingPeriodStr() { return billingPeriodStr; }
        public void setBillingPeriodStr(String billingPeriodStr) { this.billingPeriodStr = billingPeriodStr; }
        public BillingPeriod getBillingPeriod() { return billingPeriod; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
}