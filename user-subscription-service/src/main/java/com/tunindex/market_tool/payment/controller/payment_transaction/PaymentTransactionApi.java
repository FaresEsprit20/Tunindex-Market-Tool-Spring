package com.tunindex.market_tool.payment.controller.payment_transaction;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.PaymentRequestDto;
import com.tunindex.market_tool.payment.dto.PaymentResponseDto;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "Payment Transactions", description = "API for managing payment transactions")
@Validated
public interface PaymentTransactionApi {

    String BASE_URL = "/api/payments";

    // ==================== GET ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get payment transaction by ID",
            description = "Retrieves a payment transaction by its unique identifier"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction found successfully"),
            @ApiResponse(responseCode = "404", description = "Transaction not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid ID format", content = @Content)
    })
    ResponseEntity<PaymentResponseDto> getTransactionById(
            @Parameter(description = "Transaction ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    @GetMapping(value = BASE_URL + "/reference/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get payment transaction by reference",
            description = "Retrieves a payment transaction by its unique transaction reference"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction found successfully"),
            @ApiResponse(responseCode = "404", description = "Transaction not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid transaction reference", content = @Content)
    })
    ResponseEntity<PaymentResponseDto> getTransactionByReference(
            @Parameter(description = "Transaction reference", required = true, example = "TXN-1705123456789-ABC123")
            @PathVariable @NotNull String transactionId
    );

    @GetMapping(value = BASE_URL + "/user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get all transactions by user ID",
            description = "Retrieves paginated list of payment transactions for a specific user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<PaymentResponseDto>> getTransactionsByUserId(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId,

            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection,

            @Parameter(description = "Filter by status", example = "COMPLETED")
            @RequestParam(required = false) PaymentStatus status
    );

    @GetMapping(value = BASE_URL + "/status/{status}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get transactions by status",
            description = "Retrieves paginated list of payment transactions filtered by status"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status or pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<PaymentResponseDto>> getTransactionsByStatus(
            @Parameter(description = "Payment status", required = true,
                    schema = @Schema(allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED", "REFUNDED", "CANCELLED", "EXPIRED"}))
            @PathVariable PaymentStatus status,

            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection
    );

    // ==================== POST ENDPOINTS ====================

    @PostMapping(value = BASE_URL + "/initiate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Initiate a payment",
            description = "Creates a new payment transaction and returns payment details"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment initiated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request", content = @Content)
    })
    ResponseEntity<PaymentResponseDto> initiatePayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payment request details",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Monthly subscription payment",
                                            value = """
                                            {
                                                "userId": 1,
                                                "planId": 1,
                                                "amount": 29.99,
                                                "currency": "TND",
                                                "paymentMethod": "CREDIT_CARD",
                                                "billingPeriod": "MONTHLY"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Yearly subscription payment",
                                            value = """
                                            {
                                                "userId": 1,
                                                "planId": 2,
                                                "amount": 299.99,
                                                "currency": "TND",
                                                "paymentMethod": "BANK_TRANSFER",
                                                "billingPeriod": "YEARLY"
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody PaymentRequestDto paymentRequest
    );

    @PostMapping(value = BASE_URL + "/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Filter payment transactions",
            description = """
                    Filter payment transactions using multiple criteria including:
                    - userId: Filter by user ID
                    - transactionId: Search by transaction reference (partial match)
                    - status: Filter by payment status
                    - paymentMethod: Filter by payment method
                    - currency: Filter by currency code
                    - minAmount/maxAmount: Filter by amount range
                    - paymentDateFrom/paymentDateTo: Filter by payment date range
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transactions filtered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<PaymentResponseDto>> filterTransactions(
            @Valid @RequestBody PaginationAndFilteringDto paginationDto
    );

    // ==================== PUT ENDPOINTS ====================

    @PutMapping(value = BASE_URL + "/{transactionId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update transaction status",
            description = "Updates the status of a payment transaction"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Transaction not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid status transition", content = @Content)
    })
    ResponseEntity<PaymentResponseDto> updateTransactionStatus(
            @Parameter(description = "Transaction reference", required = true, example = "TXN-1705123456789-ABC123")
            @PathVariable @NotNull String transactionId,

            @Parameter(description = "New payment status", required = true,
                    schema = @Schema(allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED", "REFUNDED", "CANCELLED", "EXPIRED"}))
            @RequestParam @NotNull PaymentStatus status
    );

    @PutMapping(value = BASE_URL + "/{transactionId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Mark transaction as completed",
            description = "Marks a payment transaction as COMPLETED"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction marked as completed"),
            @ApiResponse(responseCode = "404", description = "Transaction not found", content = @Content)
    })
    ResponseEntity<PaymentResponseDto> markAsCompleted(
            @Parameter(description = "Transaction reference", required = true, example = "TXN-1705123456789-ABC123")
            @PathVariable @NotNull String transactionId
    );

    @PutMapping(value = BASE_URL + "/{transactionId}/fail", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Mark transaction as failed",
            description = "Marks a payment transaction as FAILED with a reason"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction marked as failed"),
            @ApiResponse(responseCode = "404", description = "Transaction not found", content = @Content)
    })
    ResponseEntity<PaymentResponseDto> markAsFailed(
            @Parameter(description = "Transaction reference", required = true, example = "TXN-1705123456789-ABC123")
            @PathVariable @NotNull String transactionId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Failure reason",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(value = """
                                    {
                                        "reason": "Insufficient funds"
                                    }
                                    """)
                            }
                    )
            )
            @RequestBody @NotNull String reason
    );

    // ==================== STATISTICS ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/statistics/total-spent/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get total amount spent by user",
            description = "Returns the total amount spent by a user (completed payments only)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Total amount retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid user ID", content = @Content)
    })
    ResponseEntity<BigDecimal> getTotalAmountSpentByUser(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId
    );

    @GetMapping(value = BASE_URL + "/statistics/successful-count/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get successful payments count for user",
            description = "Returns the number of successful payments made by a user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid user ID", content = @Content)
    })
    ResponseEntity<Long> getSuccessfulPaymentsCount(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId
    );
}