package com.tunindex.market_tool.payment.controller.refund;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.RefundRequestDto;
import com.tunindex.market_tool.payment.dto.RefundResponseDto;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
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

@Tag(name = "Refunds", description = "API for managing payment refunds")
@Validated
public interface RefundApi {

    String BASE_URL = "/api/refunds";

    // ==================== GET ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get refund by ID",
            description = "Retrieves a refund by its unique identifier"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund found successfully"),
            @ApiResponse(responseCode = "404", description = "Refund not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid ID format", content = @Content)
    })
    ResponseEntity<RefundResponseDto> getRefundById(
            @Parameter(description = "Refund ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    @GetMapping(value = BASE_URL + "/transaction/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get refunds by transaction ID",
            description = "Retrieves paginated list of refunds for a specific transaction"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refunds retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<RefundResponseDto>> getRefundsByTransactionId(
            @Parameter(description = "Transaction ID", required = true, example = "100")
            @PathVariable @NotNull @Positive Long transactionId,

            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection
    );

    @GetMapping(value = BASE_URL + "/status/{status}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get refunds by status",
            description = "Retrieves paginated list of refunds filtered by status"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refunds retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status or pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<RefundResponseDto>> getRefundsByStatus(
            @Parameter(description = "Refund status", required = true,
                    schema = @Schema(allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"}))
            @PathVariable RefundStatus status,

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

    @PostMapping(value = BASE_URL + "/request", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Request a refund",
            description = "Creates a new refund request for a completed transaction"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund requested successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid refund request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Transaction not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Refund already processed", content = @Content)
    })
    ResponseEntity<RefundResponseDto> requestRefund(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refund request details",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Full refund",
                                            value = """
                                            {
                                                "transactionId": 100,
                                                "amount": 99.99,
                                                "reason": "Customer requested cancellation"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Partial refund",
                                            value = """
                                            {
                                                "transactionId": 100,
                                                "amount": 49.99,
                                                "reason": "Partial service issue"
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody RefundRequestDto refundRequest
    );

    @PostMapping(value = BASE_URL + "/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Filter refunds",
            description = """
                    Filter refunds using multiple criteria including:
                    - transactionId: Filter by transaction ID
                    - status: Filter by refund status
                    - reason: Search by reason (partial match)
                    - minAmount/maxAmount: Filter by amount range
                    - refundDateFrom/refundDateTo: Filter by refund date range
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refunds filtered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<RefundResponseDto>> filterRefunds(
            @Valid @RequestBody PaginationAndFilteringDto paginationDto
    );

    // ==================== PUT ENDPOINTS ====================

    @PutMapping(value = BASE_URL + "/{refundId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update refund status",
            description = "Updates the status of a refund"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Refund not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid status transition", content = @Content)
    })
    ResponseEntity<RefundResponseDto> updateRefundStatus(
            @Parameter(description = "Refund ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long refundId,

            @Parameter(description = "New refund status", required = true,
                    schema = @Schema(allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"}))
            @RequestParam @NotNull RefundStatus status
    );

    @PutMapping(value = BASE_URL + "/{refundId}/complete", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Mark refund as completed",
            description = "Marks a refund as COMPLETED"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund marked as completed"),
            @ApiResponse(responseCode = "404", description = "Refund not found", content = @Content)
    })
    ResponseEntity<RefundResponseDto> markRefundAsCompleted(
            @Parameter(description = "Refund ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long refundId
    );

    @PutMapping(value = BASE_URL + "/{refundId}/fail", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Mark refund as failed",
            description = "Marks a refund as FAILED with a reason"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Refund marked as failed"),
            @ApiResponse(responseCode = "404", description = "Refund not found", content = @Content)
    })
    ResponseEntity<RefundResponseDto> markRefundAsFailed(
            @Parameter(description = "Refund ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long refundId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Failure reason",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(value = """
                                    {
                                        "reason": "Insufficient balance in merchant account"
                                    }
                                    """)
                            }
                    )
            )
            @RequestBody @NotNull String reason
    );

    // ==================== STATISTICS ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/statistics/total-refunded/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get total refunded amount for transaction",
            description = "Returns the total amount refunded for a specific transaction"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Total refunded amount retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid transaction ID", content = @Content)
    })
    ResponseEntity<BigDecimal> getTotalRefundedAmount(
            @Parameter(description = "Transaction ID", required = true, example = "100")
            @PathVariable @NotNull @Positive Long transactionId
    );

    @GetMapping(value = BASE_URL + "/statistics/exists/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Check if refund exists for transaction",
            description = "Returns whether a completed refund exists for a transaction"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Check completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid transaction ID", content = @Content)
    })
    ResponseEntity<Boolean> hasExistingRefund(
            @Parameter(description = "Transaction ID", required = true, example = "100")
            @PathVariable @NotNull @Positive Long transactionId
    );
}