package com.tunindex.market_tool.payment.controller.invoices;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.InvoiceDto;
import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;
import com.tunindex.market_tool.payment.service.invoices.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class InvoiceController implements InvoiceApi {

    private final InvoiceService invoiceService;

    @Override
    public ResponseEntity<InvoiceDto> getInvoiceById(Long id) {
        log.info("GET /api/invoices/{}", id);
        InvoiceDto invoice = invoiceService.findById(id);
        return ResponseEntity.ok(invoice);
    }

    @Override
    public ResponseEntity<InvoiceDto> getInvoiceByNumber(String invoiceNumber) {
        log.info("GET /api/invoices/number/{}", invoiceNumber);
        InvoiceDto invoice = invoiceService.findByInvoiceNumber(invoiceNumber);
        return ResponseEntity.ok(invoice);
    }

    @Override
    public ResponseEntity<InvoiceDto> getInvoiceByTransactionId(Long transactionId) {
        log.info("GET /api/invoices/transaction/{}", transactionId);
        InvoiceDto invoice = invoiceService.findByTransactionId(transactionId);
        return ResponseEntity.ok(invoice);
    }

    @Override
    public ResponseEntity<PagedResponse<InvoiceDto>> getInvoicesByUserId(
            Long userId, int page, int size, String sortField, String sortDirection, InvoiceStatus status) {

        log.info("GET /api/invoices/user/{} - page: {}, size: {}, sortField: {}, sortDirection: {}, status: {}",
                userId, page, size, sortField, sortDirection, status);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        if (status != null) {
            Map<String, String> filters = new HashMap<>();
            filters.put("status", status.name());
            paginationDto.setFilters(filters);
        }

        PagedResponse<InvoiceDto> response = invoiceService.findAllByUserId(userId, paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<InvoiceDto>> getInvoicesByStatus(
            InvoiceStatus status, int page, int size, String sortField, String sortDirection) {

        log.info("GET /api/invoices/status/{} - page: {}, size: {}, sortField: {}, sortDirection: {}",
                status, page, size, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        PagedResponse<InvoiceDto> response = invoiceService.findAllByStatus(status, paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<InvoiceDto>> getOverdueInvoices(
            int page, int size, String sortField, String sortDirection) {

        log.info("GET /api/invoices/overdue - page: {}, size: {}, sortField: {}, sortDirection: {}",
                page, size, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        PagedResponse<InvoiceDto> response = invoiceService.findOverdueInvoices(paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<InvoiceDto>> filterInvoices(PaginationAndFilteringDto paginationDto) {
        log.info("POST /api/invoices/filter - page: {}, size: {}, filters: {}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        PagedResponse<InvoiceDto> response = invoiceService.filterInvoices(paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<InvoiceDto> createInvoice(InvoiceDto invoiceDto) {
        log.info("POST /api/invoices - Creating invoice for user: {}", invoiceDto.getUserId());

        InvoiceDto createdInvoice = invoiceService.createInvoice(invoiceDto);
        return ResponseEntity.ok(createdInvoice);
    }

    @Override
    public ResponseEntity<InvoiceDto> updateInvoiceStatus(Long id, InvoiceStatus status) {
        log.info("PUT /api/invoices/{}/status - New status: {}", id, status);

        InvoiceDto updatedInvoice = invoiceService.updateInvoiceStatus(id, status);
        return ResponseEntity.ok(updatedInvoice);
    }

    @Override
    public ResponseEntity<InvoiceDto> markInvoiceAsPaid(Long transactionId) {
        log.info("PUT /api/invoices/mark-paid/{}", transactionId);

        InvoiceDto updatedInvoice = invoiceService.markAsPaid(transactionId);
        return ResponseEntity.ok(updatedInvoice);
    }

    @Override
    public ResponseEntity<Void> deleteInvoice(Long id) {
        log.info("DELETE /api/invoices/{}", id);

        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<BigDecimal> getTotalInvoicedAmount(Long userId, InvoiceStatus status) {
        log.info("GET /api/invoices/statistics/total-amount/{} - status: {}", userId, status);

        BigDecimal totalAmount = invoiceService.getTotalInvoicedAmountByUser(userId, status);
        return ResponseEntity.ok(totalAmount);
    }

    // ==================== EXPORT ENDPOINTS ====================

    @Override
    public ResponseEntity<byte[]> exportAllToPdf(String sortField, String sortDirection, InvoiceStatus status) {
        log.info("GET /api/invoices/export/pdf - sortField: {}, sortDirection: {}, status: {}",
                sortField, sortDirection, status);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(500);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        if (status != null) {
            Map<String, String> filters = new HashMap<>();
            filters.put("status", status.name());
            paginationDto.setFilters(filters);
        }

        byte[] pdfBytes = invoiceService.exportInvoicesToPdf(paginationDto);
        String filename = "invoices_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @Override
    public ResponseEntity<byte[]> exportAllToCsv(String sortField, String sortDirection, InvoiceStatus status) {
        log.info("GET /api/invoices/export/csv - sortField: {}, sortDirection: {}, status: {}",
                sortField, sortDirection, status);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(500);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        if (status != null) {
            Map<String, String> filters = new HashMap<>();
            filters.put("status", status.name());
            paginationDto.setFilters(filters);
        }

        byte[] csvBytes = invoiceService.exportInvoicesToCsv(paginationDto);
        String filename = "invoices_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.TEXT_PLAIN)
                .body(csvBytes);
    }

    @Override
    public ResponseEntity<byte[]> exportUserInvoicesToPdf(Long userId, String sortField, String sortDirection) {
        log.info("GET /api/invoices/user/{}/export/pdf - sortField: {}, sortDirection: {}", userId, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(500);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        byte[] pdfBytes = invoiceService.exportUserInvoicesToPdf(userId, paginationDto);
        String filename = "invoices_user_" + userId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @Override
    public ResponseEntity<byte[]> exportUserInvoicesToCsv(Long userId, String sortField, String sortDirection) {
        log.info("GET /api/invoices/user/{}/export/csv - sortField: {}, sortDirection: {}", userId, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(500);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        byte[] csvBytes = invoiceService.exportUserInvoicesToCsv(userId, paginationDto);
        String filename = "invoices_user_" + userId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.TEXT_PLAIN)
                .body(csvBytes);
    }
}