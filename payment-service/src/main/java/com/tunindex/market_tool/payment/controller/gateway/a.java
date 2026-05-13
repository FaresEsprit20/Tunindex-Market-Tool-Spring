package com.tunindex.market_tool.payment.controller.gateway;

import com.tunindex.market_tool.payment.controller.user_subscription.PaymentStatusResponseDto;
import com.tunindex.market_tool.payment.dto.CreatePaymentRequestDto;
import com.tunindex.market_tool.payment.dto.CreatePaymentResponseDto;
import com.tunindex.market_tool.payment.dto.PaymentStatusRequestDto;
import com.tunindex.market_tool.payment.dto.RefundPaymentRequestDto;
import com.tunindex.market_tool.payment.dto.RefundPaymentResponseDto;
import com.tunindex.market_tool.payment.service.gateway.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayController implements PaymentGatewayApi {

    private final PaymentGatewayService paymentGatewayService;

    @Override
    public ResponseEntity<CreatePaymentResponseDto> createPayment(CreatePaymentRequestDto request) {
        log.info("POST /api/payments/create - User: {}, Amount: {}", request.getUserId(), request.getAmount());
        CreatePaymentResponseDto response = paymentGatewayService.createPayment(request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentStatusResponseDto> getPaymentStatus(PaymentStatusRequestDto request) {
        log.info("POST /api/payments/status - Transaction: {}", request.getTransactionId());
        PaymentStatusResponseDto response = paymentGatewayService.getPaymentStatus(request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RefundPaymentResponseDto> refundPayment(RefundPaymentRequestDto request) {
        log.info("POST /api/payments/refund - Transaction: {}, Amount: {}", request.getTransactionId(), request.getAmount());
        RefundPaymentResponseDto response = paymentGatewayService.refundPayment(request);
        return ResponseEntity.ok(response);
    }
}