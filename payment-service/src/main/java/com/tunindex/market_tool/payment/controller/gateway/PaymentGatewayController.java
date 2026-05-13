package com.tunindex.market_tool.payment.controller.gateway;

import com.tunindex.market_tool.payment.controller.user_subscription.PaymentStatusResponseDto;
import com.tunindex.market_tool.payment.dto.*;
import com.tunindex.market_tool.payment.service.gateway.PaymentGatewayService;
import com.tunindex.market_tool.payment.validators.RefundPaymentRequestValidator;
import com.tunindex.market_tool.payment.validators.gateway.CreatePaymentRequestValidator;
import com.tunindex.market_tool.payment.validators.gateway.PaymentStatusRequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Payment Gateway Controller
 *
 * Handles all payment-related operations:
 * - Creating new payments (redirects to Konnect)
 * - Checking payment status
 * - Processing refunds
 *
 * This controller implements PaymentGatewayApi interface which contains
 * OpenAPI documentation for all endpoints.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayController implements PaymentGatewayApi {

    private final PaymentGatewayService paymentGatewayService;

    @Value("${payment.success-redirect-url}")
    private String successRedirectUrl;

    @Value("${payment.cancel-redirect-url}")
    private String cancelRedirectUrl;

    @Value("${payment.webhook-url}")
    private String webhookUrl;

    /**
     * Generates a unique transaction ID for each payment
     * Format: TXN-{timestamp}-{random 8 chars}
     *
     * @return unique transaction identifier
     */
    private String generateTransactionId() {
        return "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Creates a new payment and returns a payment URL
     *
     * Flow:
     * 1. Validates the payment request
     * 2. Builds gateway request with transaction ID
     * 3. Calls Konnect API to create payment
     * 4. Returns payment URL for frontend redirect
     *
     * @param request payment details (userId, planId, amount, customer info)
     * @return response containing transaction ID, payment URL, and status
     */
    @Override
    public ResponseEntity<CreatePaymentResponseDto> createPayment(CreatePaymentRequestDto request) {
        log.info("POST /api/payments/create - User: {}, Amount: {}", request.getUserId(), request.getAmount());

        // Validate request data before processing
        CreatePaymentRequestValidator.validate(request);

        // Build internal gateway request
        com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayRequest gatewayRequest =
                com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayRequest.builder()
                        .transactionId(generateTransactionId())
                        .userId(request.getUserId())
                        .planId(request.getPlanId())
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .customerEmail(request.getCustomerEmail())
                        .customerName(request.getCustomerName())
                        .customerPhone(request.getCustomerPhone())
                        .successUrl(successRedirectUrl)
                        .cancelUrl(cancelRedirectUrl)
                        .webhookUrl(webhookUrl)
                        .metadata(Map.of(
                                "billingPeriod", request.getBillingPeriod(),
                                "couponCode", request.getCouponCode() != null ? request.getCouponCode() : ""
                        ))
                        .build();

        // Call Konnect gateway to create payment
        com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayResponse gatewayResponse =
                paymentGatewayService.createPayment(gatewayRequest);

        // Build and return response for frontend
        CreatePaymentResponseDto response = CreatePaymentResponseDto.builder()
                .transactionId(gatewayResponse.getTransactionId())
                .providerPaymentId(gatewayResponse.getProviderPaymentId())
                .paymentUrl(gatewayResponse.getPaymentUrl())
                .status(gatewayResponse.getStatus())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Gets the current status of a payment
     *
     * Used by frontend to poll for payment completion after user
     * is redirected back from Konnect payment page.
     *
     * @param request contains transactionId and providerPaymentId
     * @return current payment status (PENDING, COMPLETED, FAILED, etc.)
     */
    @Override
    public ResponseEntity<PaymentStatusResponseDto> getPaymentStatus(PaymentStatusRequestDto request) {
        log.info("POST /api/payments/status - Transaction: {}", request.getTransactionId());

        // Validate request before processing
        PaymentStatusRequestValidator.validate(request);

        // Build gateway status request
        com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayStatusRequest gatewayRequest =
                com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayStatusRequest.builder()
                        .providerPaymentId(request.getProviderPaymentId())
                        .transactionId(request.getTransactionId())
                        .build();

        // Query Konnect for payment status
        com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayStatusResponse gatewayResponse =
                paymentGatewayService.getPaymentStatus(gatewayRequest);

        // Convert String payment method from gateway to Enum
        PaymentMethodType paymentMethod = null;
        if (gatewayResponse.getPaymentMethod() != null) {
            try {
                paymentMethod = PaymentMethodType.valueOf(gatewayResponse.getPaymentMethod().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown payment method: {}", gatewayResponse.getPaymentMethod());
            }
        }

        // Build response for frontend
        PaymentStatusResponseDto response = PaymentStatusResponseDto.builder()
                .transactionId(gatewayResponse.getTransactionId())
                .providerPaymentId(gatewayResponse.getProviderPaymentId())
                .status(gatewayResponse.getStatus())
                .amount(gatewayResponse.getAmount())
                .currency(gatewayResponse.getCurrency())
                .paymentMethod(paymentMethod)
                .paymentDate(gatewayResponse.getPaymentDate())
                .failureReason(gatewayResponse.getFailureReason())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Processes a refund for a completed payment
     *
     * Note: This endpoint should be secured (Admin only)
     *
     * @param request contains transactionId, providerPaymentId, amount, and reason
     * @return refund confirmation details
     */
    @Override
    public ResponseEntity<RefundPaymentResponseDto> refundPayment(RefundPaymentRequestDto request) {
        log.info("POST /api/payments/refund - Transaction: {}, Amount: {}", request.getTransactionId(), request.getAmount());

        // Validate refund request before processing
        RefundPaymentRequestValidator.validate(request);

        // Call Konnect gateway to process refund
        com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayResponse gatewayResponse =
                paymentGatewayService.refundPayment(request.getProviderPaymentId(), request.getAmount(), request.getReason());

        // Build refund response
        RefundPaymentResponseDto response = RefundPaymentResponseDto.builder()
                .transactionId(request.getTransactionId())
                .providerRefundId(gatewayResponse.getProviderPaymentId())
                .amount(request.getAmount())
                .status(gatewayResponse.getStatus())
                .refundDate(java.time.LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }
}