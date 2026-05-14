package com.tunindex.market_tool.payment.controller.gateway;

import com.tunindex.market_tool.payment.controller.user_subscription.PaymentStatusResponseDto;
import com.tunindex.market_tool.payment.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Tag(name = "Payment Gateway", description = "API for payment processing")
@RequestMapping("/api/payments")
public interface PaymentGatewayApi {

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a new payment",
            description = "Initiates a payment request with the configured payment gateway. Returns a payment URL to redirect the user."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters", content = @Content),
            @ApiResponse(responseCode = "500", description = "Payment gateway error", content = @Content)
    })
    ResponseEntity<CreatePaymentResponseDto> createPayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payment creation request",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Monthly subscription",
                                            value = """
                                            {
                                                "userId": 1,
                                                "planId": 1,
                                                "amount": 29.99,
                                                "currency": "TND",
                                                "billingPeriod": "MONTHLY",
                                                "customerEmail": "user@example.com",
                                                "customerName": "John Doe",
                                                "customerPhone": "+21612345678"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Yearly subscription",
                                            value = """
                                            {
                                                "userId": 1,
                                                "planId": 2,
                                                "amount": 299.99,
                                                "currency": "TND",
                                                "billingPeriod": "YEARLY",
                                                "customerEmail": "user@example.com",
                                                "customerName": "John Doe"
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody CreatePaymentRequestDto request
    );


    @GetMapping(value = "/payment-methods", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get available payment methods",
            description = "Returns the list of payment methods configured and available for the user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment methods retrieved successfully")
    })
    ResponseEntity<List<PaymentMethodResponseDto>> getAvailablePaymentMethods();

    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get payment status",
            description = "Retrieves the current status of a payment transaction"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
    })
    ResponseEntity<PaymentStatusResponseDto> getPaymentStatus(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payment status request",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            value = """
                                            {
                                                "transactionId": "TXN-1705123456789-ABC123",
                                                "providerPaymentId": "pay_123456789"
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody PaymentStatusRequestDto request
    );

    @PostMapping(value = "/refund", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Refund a payment",
            description = "Processes a refund for a completed payment"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid refund request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
    })
    ResponseEntity<RefundPaymentResponseDto> refundPayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refund request",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Full refund",
                                            value = """
                                            {
                                                "transactionId": "TXN-1705123456789-ABC123",
                                                "providerPaymentId": "pay_123456789",
                                                "amount": 29.99,
                                                "reason": "Customer requested cancellation"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Partial refund",
                                            value = """
                                            {
                                                "transactionId": "TXN-1705123456789-ABC123",
                                                "providerPaymentId": "pay_123456789",
                                                "amount": 14.99,
                                                "reason": "Partial service issue"
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody RefundPaymentRequestDto request
    );
}