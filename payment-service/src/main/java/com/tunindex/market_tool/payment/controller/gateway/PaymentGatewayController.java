package com.tunindex.market_tool.payment.controller.gateway;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.payment.client.ApiServiceClient;
import com.tunindex.market_tool.payment.dto.*;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayRequest;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayResponse;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayStatusRequest;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayStatusResponse;
import com.tunindex.market_tool.payment.service.gateway.PaymentGatewayService;
import com.tunindex.market_tool.payment.service.gateway.PaymentMethodService;
import com.tunindex.market_tool.payment.validators.gateway.CreatePaymentRequestValidator;
import com.tunindex.market_tool.payment.validators.gateway.PaymentGatewayRequestValidator;
import com.tunindex.market_tool.payment.validators.gateway.PaymentGatewayStatusRequestValidator;
import com.tunindex.market_tool.payment.validators.gateway.PaymentStatusRequestValidator;
import com.tunindex.market_tool.payment.validators.gateway.RefundPaymentRequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayController implements PaymentGatewayApi {

    private final PaymentGatewayService paymentGatewayService;
    private final PaymentMethodService paymentMethodService;
    private final WebClient.Builder webClientBuilder;
    private final ApiServiceClient apiServiceClient;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Value("${payment.service.url:http://payment-service}")
    private String paymentServiceUrl;

    @Value("${payment.success-redirect-url}")
    private String successRedirectUrl;

    @Value("${payment.cancel-redirect-url}")
    private String cancelRedirectUrl;

    @Value("${payment.webhook-url}")
    private String webhookUrl;

    private String generateTransactionId() {
        return "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public ResponseEntity<CreatePaymentResponseDto> createPayment(CreatePaymentRequestDto request) {
        log.info("POST /api/payments/create - User: {}, Amount: {}", request.getUserId(), request.getAmount());

        CreatePaymentRequestValidator.validate(request);

        PaymentGatewayRequest gatewayRequest = PaymentGatewayRequest.builder()
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

        PaymentGatewayRequestValidator.validate(gatewayRequest);

        PaymentGatewayResponse gatewayResponse = paymentGatewayService.createPayment(gatewayRequest);

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

    @Override
    public ResponseEntity<PaymentStatusResponseDto> getPaymentStatus(PaymentStatusRequestDto request) {
        log.info("POST /api/payments/status - Transaction: {}", request.getTransactionId());

        PaymentStatusRequestValidator.validate(request);

        PaymentGatewayStatusRequest gatewayRequest = PaymentGatewayStatusRequest.builder()
                .providerPaymentId(request.getProviderPaymentId())
                .transactionId(request.getTransactionId())
                .build();

        PaymentGatewayStatusRequestValidator.validate(gatewayRequest);

        PaymentGatewayStatusResponse gatewayResponse = paymentGatewayService.getPaymentStatus(gatewayRequest);

        PaymentMethodType paymentMethod = null;
        if (gatewayResponse.getPaymentMethod() != null) {
            try {
                paymentMethod = gatewayResponse.getPaymentMethod();
            } catch (IllegalArgumentException e) {
                log.warn("Unknown payment method: {}", gatewayResponse.getPaymentMethod());
            }
        }

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

    @Override
    public ResponseEntity<RefundPaymentResponseDto> refundPayment(RefundPaymentRequestDto request) {
        log.info("POST /api/payments/refund - Transaction: {}, Amount: {}", request.getTransactionId(), request.getAmount());

        // 1. Validate the request
        RefundPaymentRequestValidator.validate(request);


        // 3. Call internal refund controller using WebClient
        String internalUrl = paymentServiceUrl + "/internal/refund/process";

        try {
            Map<String, Object> internalResponse = webClientBuilder.build()
                    .post()
                    .uri(internalUrl)
                    .header("X-API-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (internalResponse != null && Boolean.TRUE.equals(internalResponse.get("success"))) {
                return ResponseEntity.ok(RefundPaymentResponseDto.builder()
                        .transactionId(request.getTransactionId())
                        .providerRefundId((String) internalResponse.get("refundId"))
                        .amount(request.getAmount())
                        .status("REFUNDED")
                        .refundDate(java.time.LocalDateTime.now())
                        .build());
            } else {
                throw new RuntimeException("Refund processing failed");
            }

        } catch (Exception e) {
            log.error("Refund failed: {}", e.getMessage());
            throw new InvalidOperationException(
                    "Failed to process refund: " + e.getMessage(),
                    ErrorCodes.PAYMENT_REFUND_FAILED,
                    List.of(e.getMessage())
            );
        }
    }

    @Override
    public ResponseEntity<List<PaymentMethodResponseDto>> getAvailablePaymentMethods() {
        log.info("GET /api/payments/payment-methods");
        return ResponseEntity.ok(paymentMethodService.getAvailablePaymentMethods());
    }

}