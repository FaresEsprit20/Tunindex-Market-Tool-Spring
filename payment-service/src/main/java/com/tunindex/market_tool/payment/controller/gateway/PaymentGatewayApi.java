package com.tunindex.market_tool.payment.controller.gateway;

import com.tunindex.market_tool.payment.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Payment Gateway", description = "API for payment processing")
public interface PaymentGatewayApi {

    String BASE_URL = "/api/payments";

    @PostMapping(value = BASE_URL + "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new payment", description = "Initiates a payment request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters", content = @Content)
    })
    ResponseEntity<CreatePaymentResponseDto> createPayment(@Valid @RequestBody CreatePaymentRequestDto request);

    @PostMapping(value = BASE_URL + "/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get payment status", description = "Retrieves the current status of a payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
    })
    ResponseEntity<PaymentStatusResponseDto> getPaymentStatus(@Valid @RequestBody PaymentStatusRequestDto request);

    @PostMapping(value = BASE_URL + "/refund", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Refund a payment", description = "Processes a refund for a completed payment")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid refund request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Payment not found", content = @Content)
    })
    ResponseEntity<RefundPaymentResponseDto> refundPayment(@Valid @RequestBody RefundPaymentRequestDto request);

    @GetMapping(value = BASE_URL + "/payment-methods", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get available payment methods", description = "Returns list of available payment methods")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment methods retrieved successfully")
    })
    ResponseEntity<List<PaymentMethodResponseDto>> getAvailablePaymentMethods();
}