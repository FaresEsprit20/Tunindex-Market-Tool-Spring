package com.tunindex.market_tool.payment.controller.refund;

import com.tunindex.market_tool.payment.dto.RefundPaymentRequestDto;
import com.tunindex.market_tool.payment.dto.RefundResponseDto;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayResponse;
import com.tunindex.market_tool.payment.service.gateway.PaymentGatewayService;
import com.tunindex.market_tool.payment.service.refund.RefundService;
import com.tunindex.market_tool.payment.validators.gateway.RefundPaymentRequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal/refund")
@RequiredArgsConstructor
@Slf4j
public class InternalRefundController {

    private final RefundService refundService;
    private final PaymentGatewayService paymentGatewayService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("Invalid internal API key for refund");
            throw new SecurityException("Invalid API key");
        }
    }

    /**
     * Process a refund (called by subscription service or admin)
     * POST /internal/refund/process
     */
    @PostMapping("/process")
    public ResponseEntity<?> processRefund(
            @RequestBody RefundPaymentRequestDto request,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestHeader("X-User-Id") Long userId) {

        validateApiKey(apiKey);
        log.info("Internal refund request for transaction: {} by user: {}", request.getTransactionId(), userId);

        try {
            // 1. Validate request
            RefundPaymentRequestValidator.validate(request);

            // 2. Create refund record (PENDING)
            RefundResponseDto refundResponse = refundService.requestRefund(request, userId);

            // 3. Process with Konnect
            if (refundResponse.getStatus().name().equals("PENDING")) {
                PaymentGatewayResponse gatewayResponse = paymentGatewayService.refundPayment(
                        request.getProviderPaymentId(),
                        request.getAmount(),
                        request.getReason()
                );

                // 4. Update status based on response
                if ("REFUNDED".equals(gatewayResponse.getStatus())) {
                    refundService.markAsCompleted(refundResponse.getId());
                    log.info("✅ Refund completed for transaction: {}", request.getTransactionId());
                } else {
                    refundService.markAsFailed(refundResponse.getId(), "Konnect refund failed");
                    log.error("❌ Konnect refund failed for transaction: {}", request.getTransactionId());
                }

                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "refundId", refundResponse.getId(),
                        "transactionId", request.getTransactionId(),
                        "status", gatewayResponse.getStatus(),
                        "message", "Refund processed successfully"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Refund could not be processed"
            ));

        } catch (Exception e) {
            log.error("Refund processing failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Check refund status (called by subscription service)
     * GET /internal/refund/status/{transactionId}
     */
    @GetMapping("/status/{transactionId}")
    public ResponseEntity<?> getRefundStatus(
            @PathVariable String transactionId,
            @RequestHeader("X-API-Key") String apiKey) {

        validateApiKey(apiKey);
        log.info("Checking refund status for transaction: {}", transactionId);

        // Find refund by transaction ID
        // Implementation depends on your repository

        return ResponseEntity.ok(Map.of(
                "transactionId", transactionId,
                "refunded", false,
                "status", "NOT_FOUND"
        ));
    }
}