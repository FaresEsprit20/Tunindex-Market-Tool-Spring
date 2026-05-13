package com.tunindex.market_tool.payment.controller.invoices;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.InvoiceDto;
import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;
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

@Tag(name = "Invoices", description = "API for managing payment invoices")
@Validated
public interface InvoiceApi {

    String BASE_URL = "/api/invoices";

    // ==================== GET ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get invoice by ID",
            description = "Retrieves an invoice by its unique identifier"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice found successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid ID format", content = @Content)
    })
    ResponseEntity<InvoiceDto> getInvoiceById(
            @Parameter(description = "Invoice ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    @GetMapping(value = BASE_URL + "/number/{invoiceNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get invoice by invoice number",
            description = "Retrieves an invoice by its unique invoice number"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice found successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid invoice number", content = @Content)
    })
    ResponseEntity<InvoiceDto> getInvoiceByNumber(
            @Parameter(description = "Invoice number", required = true, example = "INV-1705123456789-ABC123")
            @PathVariable @NotNull String invoiceNumber
    );

    @GetMapping(value = BASE_URL + "/transaction/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get invoice by transaction ID",
            description = "Retrieves an invoice associated with a specific payment transaction"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice found successfully"),
            @ApiResponse(responseCode = "404", description = "No invoice found for this transaction", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid transaction ID", content = @Content)
    })
    ResponseEntity<InvoiceDto> getInvoiceByTransactionId(
            @Parameter(description = "Transaction ID", required = true, example = "100")
            @PathVariable @NotNull @Positive Long transactionId
    );

    @GetMapping(value = BASE_URL + "/user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get all invoices by user ID",
            description = "Retrieves paginated list of invoices for a specific user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoices retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<InvoiceDto>> getInvoicesByUserId(
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

            @Parameter(description = "Filter by status", example = "PAID")
            @RequestParam(required = false) InvoiceStatus status
    );

    @GetMapping(value = BASE_URL + "/status/{status}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get invoices by status",
            description = "Retrieves paginated list of invoices filtered by status"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoices retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status or pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<InvoiceDto>> getInvoicesByStatus(
            @Parameter(description = "Invoice status", required = true,
                    schema = @Schema(allowableValues = {"DRAFT", "ISSUED", "PAID", "OVERDUE", "CANCELLED"}))
            @PathVariable InvoiceStatus status,

            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection
    );

    @GetMapping(value = BASE_URL + "/overdue", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get overdue invoices",
            description = "Retrieves paginated list of overdue invoices (due date passed and not paid)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Overdue invoices retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<InvoiceDto>> getOverdueInvoices(
            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size,

            @Parameter(description = "Sort field", example = "dueDate")
            @RequestParam(defaultValue = "dueDate") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "ASC")
            @RequestParam(defaultValue = "ASC") String sortDirection
    );

    // ==================== POST ENDPOINTS ====================

    @PostMapping(value = BASE_URL + "/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Filter invoices with advanced criteria",
            description = """
                    Filter invoices using multiple criteria including:
                    - userId: Filter by user ID
                    - invoiceNumber: Search by invoice number (partial match)
                    - status: Filter by invoice status
                    - currency: Filter by currency code (e.g., TND, USD)
                    - minAmount/maxAmount: Filter by amount range
                    - minTotalAmount/maxTotalAmount: Filter by total amount range
                    - issueDateFrom/issueDateTo: Filter by issue date range
                    - dueDateFrom/dueDateTo: Filter by due date range
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoices filtered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<InvoiceDto>> filterInvoices(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Pagination and filtering parameters",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Filter by user and status",
                                            value = """
                                            {
                                                "page": 1,
                                                "size": 10,
                                                "sortField": "createdAt",
                                                "sortDirection": "DESC",
                                                "filters": {
                                                    "userId": "1",
                                                    "status": "PAID"
                                                }
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Filter by amount range",
                                            value = """
                                            {
                                                "page": 1,
                                                "size": 10,
                                                "sortField": "amount",
                                                "sortDirection": "DESC",
                                                "filters": {
                                                    "minAmount": "10.00",
                                                    "maxAmount": "100.00"
                                                }
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Filter by date range",
                                            value = """
                                            {
                                                "page": 1,
                                                "size": 10,
                                                "sortField": "dueDate",
                                                "sortDirection": "ASC",
                                                "filters": {
                                                    "dueDateFrom": "2024-01-01T00:00:00",
                                                    "dueDateTo": "2024-12-31T23:59:59"
                                                }
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody PaginationAndFilteringDto paginationDto
    );

    @PostMapping(value = BASE_URL, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a new invoice",
            description = "Creates a new invoice. If invoice number is not provided, it will be auto-generated."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid invoice data", content = @Content)
    })
    ResponseEntity<InvoiceDto> createInvoice(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Invoice data",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Basic invoice",
                                            value = """
                                            {
                                                "userId": 1,
                                                "transactionId": 100,
                                                "amount": 99.99,
                                                "currency": "TND",
                                                "taxAmount": 9.99,
                                                "totalAmount": 109.98,
                                                "dueDate": "2024-12-31T23:59:59"
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody InvoiceDto invoiceDto
    );

    // ==================== PUT ENDPOINTS ====================

    @PutMapping(value = BASE_URL + "/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update invoice status",
            description = "Updates the status of an invoice. Allowed transitions depend on current status."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid status transition", content = @Content)
    })
    ResponseEntity<InvoiceDto> updateInvoiceStatus(
            @Parameter(description = "Invoice ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id,

            @Parameter(description = "New invoice status", required = true,
                    schema = @Schema(allowableValues = {"DRAFT", "ISSUED", "PAID", "OVERDUE", "CANCELLED"}))
            @RequestParam @NotNull InvoiceStatus status
    );

    @PutMapping(value = BASE_URL + "/mark-paid/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Mark invoice as paid",
            description = "Marks an invoice as paid based on the associated transaction ID"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice marked as paid successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found for transaction", content = @Content),
            @ApiResponse(responseCode = "400", description = "Cannot mark invoice as paid", content = @Content)
    })
    ResponseEntity<InvoiceDto> markInvoiceAsPaid(
            @Parameter(description = "Transaction ID", required = true, example = "100")
            @PathVariable @NotNull @Positive Long transactionId
    );

    // ==================== DELETE ENDPOINTS ====================

    @DeleteMapping(value = BASE_URL + "/{id}")
    @Operation(
            summary = "Delete invoice",
            description = "Deletes an invoice. Only DRAFT or CANCELLED invoices can be deleted."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Invoice deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Cannot delete paid invoice", content = @Content)
    })
    ResponseEntity<Void> deleteInvoice(
            @Parameter(description = "Invoice ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    // ==================== STATISTICS ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/statistics/total-amount/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get total invoiced amount by user",
            description = "Returns the total amount invoiced for a specific user, optionally filtered by status"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Total amount retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid user ID", content = @Content)
    })
    ResponseEntity<BigDecimal> getTotalInvoicedAmount(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId,

            @Parameter(description = "Filter by invoice status", example = "PAID")
            @RequestParam(required = false) InvoiceStatus status
    );
}