package com.tunindex.market_tool.payment.controller.invoices;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.InvoiceDto;
import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "Invoices", description = "API for managing payment invoices")
public interface InvoiceApi {

    String BASE_URL = "/api/invoices";

    // ==================== GET ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get invoice by ID", description = "Retrieves an invoice by its unique identifier")
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
    @Operation(summary = "Get invoice by invoice number", description = "Retrieves an invoice by its unique invoice number")
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
    @Operation(summary = "Get invoice by transaction ID", description = "Retrieves an invoice associated with a specific payment transaction")
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
    @Operation(summary = "Get all invoices by user ID", description = "Retrieves paginated list of invoices for a specific user")
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
    @Operation(summary = "Get invoices by status", description = "Retrieves paginated list of invoices filtered by status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoices retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status or pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<InvoiceDto>> getInvoicesByStatus(
            @Parameter(description = "Invoice status", required = true,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"DRAFT", "ISSUED", "PAID", "OVERDUE", "CANCELLED"}))
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
    @Operation(summary = "Get overdue invoices", description = "Retrieves paginated list of overdue invoices (due date passed and not paid)")
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
    @Operation(summary = "Filter invoices with advanced criteria", description = "Filter invoices using multiple criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoices filtered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<InvoiceDto>> filterInvoices(
            @Valid @RequestBody PaginationAndFilteringDto paginationDto
    );

    @PostMapping(value = BASE_URL, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new invoice", description = "Creates a new invoice")
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
    @Operation(summary = "Update invoice status", description = "Updates the status of an invoice")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invoice status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Invoice not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid status transition", content = @Content)
    })
    ResponseEntity<InvoiceDto> updateInvoiceStatus(
            @Parameter(description = "Invoice ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id,

            @Parameter(description = "New invoice status", required = true,
                    schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"DRAFT", "ISSUED", "PAID", "OVERDUE", "CANCELLED"}))
            @RequestParam @NotNull InvoiceStatus status
    );

    @PutMapping(value = BASE_URL + "/mark-paid/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mark invoice as paid", description = "Marks an invoice as paid based on the associated transaction ID")
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
    @Operation(summary = "Delete invoice", description = "Deletes an invoice. Only DRAFT or CANCELLED invoices can be deleted.")
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
    @Operation(summary = "Get total invoiced amount by user", description = "Returns the total amount invoiced for a specific user")
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

    // ==================== EXPORT ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Export all invoices to PDF", description = "Exports all invoices to PDF format with pagination support")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF exported successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content)
    })
    ResponseEntity<byte[]> exportAllToPdf(
            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection,

            @Parameter(description = "Filter by status", example = "PAID")
            @RequestParam(required = false) InvoiceStatus status
    );

    @GetMapping(value = BASE_URL + "/export/csv", produces = "text/csv")
    @Operation(summary = "Export all invoices to CSV", description = "Exports all invoices to CSV format with pagination support")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CSV exported successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content)
    })
    ResponseEntity<byte[]> exportAllToCsv(
            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection,

            @Parameter(description = "Filter by status", example = "PAID")
            @RequestParam(required = false) InvoiceStatus status
    );

    @GetMapping(value = BASE_URL + "/user/{userId}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Export user invoices to PDF", description = "Exports invoices for a specific user to PDF format")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF exported successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    ResponseEntity<byte[]> exportUserInvoicesToPdf(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection
    );

    @GetMapping(value = BASE_URL + "/user/{userId}/export/csv", produces = "text/csv")
    @Operation(summary = "Export user invoices to CSV", description = "Exports invoices for a specific user to CSV format")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "CSV exported successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    ResponseEntity<byte[]> exportUserInvoicesToCsv(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection
    );

}