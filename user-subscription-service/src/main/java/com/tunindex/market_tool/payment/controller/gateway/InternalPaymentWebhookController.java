package com.tunindex.market_tool.payment.controller.gateway;

import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayStatusResponse;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayWebhookPayload;
import com.tunindex.market_tool.payment.service.gateway.PaymentGatewayService;
import com.tunindex.market_tool.payment.validators.WebhookValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/internal/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class InternalPaymentWebhookController {

    private final PaymentGatewayService paymentGatewayService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @PostMapping("/konnect")
    public ResponseEntity<?> handleKonnectWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        // Validate API key
        if (!internalApiKey.equals(apiKey)) {
            log.warn("Invalid API key for webhook");
            return ResponseEntity.status(401).body(Map.of("error", "Invalid API key"));
        }

        log.info("Received Konnect webhook: event={}, orderId={}",
                payload.get("event"), payload.get("order_id"));

        try {
            // Build webhook payload DTO
            PaymentGatewayWebhookPayload webhookPayload = PaymentGatewayWebhookPayload.builder()
                    .eventType((String) payload.get("event"))
                    .transactionId((String) payload.get("order_id"))
                    .providerPaymentId((String) payload.get("payment_id"))
                    .amount(new java.math.BigDecimal(Double.parseDouble(payload.get("amount").toString())))
                    .currency((String) payload.get("currency"))
                    .status((String) payload.get("status"))
                    .signature(signature)
                    .timestamp(LocalDateTime.now())
                    .build();

            // Validate webhook payload
            WebhookValidator.validate(webhookPayload, signature);

            // Process webhook
            PaymentGatewayStatusResponse response = paymentGatewayService.processWebhook(webhookPayload, signature);

            log.info("Webhook processed successfully. Transaction: {}, Status: {}",
                    response.getTransactionId(), response.getStatus());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "transactionId", response.getTransactionId(),
                    "status", response.getStatus()
            ));

        } catch (InvalidOperationException e) {
            log.error("Webhook processing failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Unexpected error processing webhook: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", "Internal server error"
            ));
        }
    }
}