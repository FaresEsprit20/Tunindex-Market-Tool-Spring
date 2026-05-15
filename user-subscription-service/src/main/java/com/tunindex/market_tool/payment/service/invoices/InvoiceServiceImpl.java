package com.tunindex.market_tool.payment.service.invoices;

import com.itextpdf.text.pdf.PdfWriter;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationUtil;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.InvoiceDto;
import com.tunindex.market_tool.payment.entities.Invoice;
import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;
import com.tunindex.market_tool.payment.repository.InvoiceRepository;
import com.tunindex.market_tool.payment.specifications.InvoiceSpecification;
import com.tunindex.market_tool.payment.validators.InvoiceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.opencsv.CSVWriter;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto findById(Long id) {
        log.info("🔍 Finding invoice by id: {}", id);

        List<String> errors = new ArrayList<>();

        if (id == null || id <= 0) {
            errors.add("Invoice ID must be a positive number");
            throw new InvalidEntityException("Invalid invoice ID", ErrorCodes.INVOICE_NOT_FOUND, errors);
        }

        return invoiceRepository.findById(id)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No invoice found with id: " + id);
                    return new EntityNotFoundException(
                            "Invoice not found with id: " + id,
                            ErrorCodes.INVOICE_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto findByInvoiceNumber(String invoiceNumber) {
        log.info("🔍 Finding invoice by number: {}", invoiceNumber);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(invoiceNumber)) {
            errors.add("Invoice number cannot be empty");
            throw new InvalidEntityException("Invalid invoice number", ErrorCodes.INVOICE_NOT_FOUND, errors);
        }

        return invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No invoice found with number: " + invoiceNumber);
                    return new EntityNotFoundException(
                            "Invoice not found with number: " + invoiceNumber,
                            ErrorCodes.INVOICE_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto findByTransactionId(Long transactionId) {
        log.info("🔍 Finding invoice by transaction id: {}", transactionId);

        List<String> errors = new ArrayList<>();

        if (transactionId == null || transactionId <= 0) {
            errors.add("Transaction ID must be a positive number");
            throw new InvalidEntityException("Invalid transaction ID", ErrorCodes.INVOICE_NOT_FOUND, errors);
        }

        return invoiceRepository.findByTransactionId(transactionId)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No invoice found with transaction id: " + transactionId);
                    return new EntityNotFoundException(
                            "Invoice not found with transaction id: " + transactionId,
                            ErrorCodes.INVOICE_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InvoiceDto> findAllByUserId(Long userId, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all invoices for user: {} with pagination", userId);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Invoice> invoicePage = invoiceRepository.findAllByUserId(userId, pageable);

        return buildPagedResponse(invoicePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InvoiceDto> findAllByStatus(InvoiceStatus status, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all invoices with status: {}", status);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Invoice> invoicePage = invoiceRepository.findAllByStatus(status, pageable);

        return buildPagedResponse(invoicePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InvoiceDto> findByUserIdAndStatus(Long userId, InvoiceStatus status, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding invoices for user: {} with status: {}", userId, status);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Invoice> invoicePage = invoiceRepository.findByUserIdAndStatus(userId, status, pageable);

        return buildPagedResponse(invoicePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InvoiceDto> findOverdueInvoices(PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding overdue invoices");

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        LocalDateTime now = LocalDateTime.now();
        Page<Invoice> invoicePage = invoiceRepository.findOverdueInvoices(now, InvoiceStatus.ISSUED, pageable);

        return buildPagedResponse(invoicePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InvoiceDto> filterInvoices(PaginationAndFilteringDto paginationDto) {
        log.info("🔍 Filtering invoices with pagination: page={}, size={}, filters={}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        validatePaginationDto(paginationDto);

        Specification<Invoice> specification = buildSpecificationFromFilters(paginationDto.getFilters());
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Invoice> invoicePage = invoiceRepository.findAll(specification, pageable);

        return buildPagedResponse(invoicePage);
    }

    @Override
    @Transactional
    public InvoiceDto createInvoice(InvoiceDto invoiceDto) {
        log.info("📝 Creating new invoice for user: {}", invoiceDto.getUserId());

        // Validate the invoice DTO
        InvoiceValidator.validate(invoiceDto);

        // Generate invoice number if not provided
        if (!StringUtils.hasLength(invoiceDto.getInvoiceNumber())) {
            invoiceDto.setInvoiceNumber(generateInvoiceNumber());
        }

        // Set default status if not provided
        if (invoiceDto.getStatus() == null) {
            invoiceDto.setStatus(InvoiceStatus.DRAFT);
        }

        // Calculate total amount if tax is provided
        if (invoiceDto.getTaxAmount() != null && invoiceDto.getAmount() != null) {
            invoiceDto.setTotalAmount(invoiceDto.getAmount().add(invoiceDto.getTaxAmount()));
        } else if (invoiceDto.getAmount() != null) {
            invoiceDto.setTotalAmount(invoiceDto.getAmount());
        }

        Invoice invoice = convertToEntity(invoiceDto);
        Invoice savedInvoice = invoiceRepository.save(invoice);

        log.info("✅ Invoice created successfully with number: {}", savedInvoice.getInvoiceNumber());
        return convertToDto(savedInvoice);
    }

    @Override
    @Transactional
    public InvoiceDto updateInvoiceStatus(Long id, InvoiceStatus newStatus) {
        log.info("🔄 Updating invoice status for id: {} to: {}", id, newStatus);

        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Invoice not found with id: " + id,
                        ErrorCodes.INVOICE_NOT_FOUND,
                        List.of("No invoice found")
                ));

        InvoiceValidator.validatePayment(invoice);
        invoice.setStatus(newStatus);

        if (newStatus == InvoiceStatus.PAID) {
            invoice.setPaidAt(LocalDateTime.now());
        }

        Invoice updatedInvoice = invoiceRepository.save(invoice);
        log.info("✅ Invoice status updated successfully");
        return convertToDto(updatedInvoice);
    }

    @Override
    @Transactional
    public InvoiceDto markAsPaid(Long transactionId) {
        log.info("💰 Marking invoice as paid for transaction: {}", transactionId);

        Invoice invoice = invoiceRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Invoice not found with transaction id: " + transactionId,
                        ErrorCodes.INVOICE_NOT_FOUND,
                        List.of("No invoice found")
                ));

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());

        Invoice updatedInvoice = invoiceRepository.save(invoice);
        log.info("✅ Invoice marked as paid");
        return convertToDto(updatedInvoice);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalInvoicedAmountByUser(Long userId, InvoiceStatus status) {
        log.info("💰 Getting total invoiced amount for user: {} with status: {}", userId, status);
        return invoiceRepository.getTotalInvoicedAmountByUser(userId, status);
    }

    @Override
    @Transactional
    public void deleteInvoice(Long id) {
        log.info("🗑️ Deleting invoice with id: {}", id);

        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Invoice not found with id: " + id,
                        ErrorCodes.INVOICE_NOT_FOUND,
                        List.of("No invoice found")
                ));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new InvalidEntityException(
                    "Cannot delete a paid invoice",
                    ErrorCodes.INVOICE_ALREADY_PAID,
                    List.of("Paid invoices cannot be deleted")
            );
        }

        invoiceRepository.delete(invoice);
        log.info("✅ Invoice deleted successfully");
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void validatePaginationDto(PaginationAndFilteringDto paginationDto) {
        List<String> errors = new ArrayList<>();

        if (paginationDto.getPage() == null || paginationDto.getPage() < 1) {
            errors.add("Page number must be greater than 0");
        }

        if (paginationDto.getSize() == null || paginationDto.getSize() < 1) {
            errors.add("Page size must be greater than 0");
        }

        if (paginationDto.getSize() != null && paginationDto.getSize() > 100) {
            errors.add("Page size cannot exceed 100");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid pagination parameters", ErrorCodes.PAGE_NOT_VALID, errors);
        }
    }

    private PagedResponse<InvoiceDto> buildPagedResponse(Page<Invoice> invoicePage) {
        List<InvoiceDto> content = invoicePage.getContent()
                .stream()
                .map(this::convertToDto)
                .toList();

        return new PagedResponse<>(
                content,
                invoicePage.getNumber() + 1,
                invoicePage.getSize(),
                invoicePage.getTotalElements(),
                invoicePage.getTotalPages()
        );
    }

    private Specification<Invoice> buildSpecificationFromFilters(Map<String, String> filters) {
        Specification<Invoice> spec = InvoiceSpecification.empty();

        if (filters == null || filters.isEmpty()) {
            return spec;
        }

        if (StringUtils.hasLength(filters.get("userId"))) {
            try {
                Long userId = Long.valueOf(filters.get("userId"));
                spec = spec.and(InvoiceSpecification.userIdEquals(userId));
            } catch (NumberFormatException e) {
                log.warn("Invalid userId value: {}", filters.get("userId"));
            }
        }

        if (StringUtils.hasLength(filters.get("invoiceNumber"))) {
            spec = spec.and(InvoiceSpecification.invoiceNumberContains(filters.get("invoiceNumber")));
        }

        if (StringUtils.hasLength(filters.get("status"))) {
            try {
                InvoiceStatus status = InvoiceStatus.valueOf(filters.get("status").toUpperCase());
                spec = spec.and(InvoiceSpecification.statusEquals(status));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", filters.get("status"));
            }
        }

        if (StringUtils.hasLength(filters.get("currency"))) {
            spec = spec.and(InvoiceSpecification.currencyEquals(filters.get("currency")));
        }

        // Amount range filters
        if (filters.containsKey("minAmount") || filters.containsKey("maxAmount")) {
            BigDecimal minAmount = parseBigDecimal(filters, "minAmount");
            BigDecimal maxAmount = parseBigDecimal(filters, "maxAmount");
            spec = spec.and(InvoiceSpecification.amountBetween(minAmount, maxAmount));
        }

        // Total amount range filters
        if (filters.containsKey("minTotalAmount") || filters.containsKey("maxTotalAmount")) {
            BigDecimal minTotal = parseBigDecimal(filters, "minTotalAmount");
            BigDecimal maxTotal = parseBigDecimal(filters, "maxTotalAmount");
            spec = spec.and(InvoiceSpecification.totalAmountBetween(minTotal, maxTotal));
        }

        // Date filters
        if (StringUtils.hasLength(filters.get("issueDateFrom"))) {
            LocalDateTime from = parseLocalDateTime(filters.get("issueDateFrom"));
            spec = spec.and(InvoiceSpecification.issueDateBetween(from, null));
        }

        if (StringUtils.hasLength(filters.get("issueDateTo"))) {
            LocalDateTime to = parseLocalDateTime(filters.get("issueDateTo"));
            spec = spec.and(InvoiceSpecification.issueDateBetween(null, to));
        }

        if (StringUtils.hasLength(filters.get("dueDateFrom"))) {
            LocalDateTime from = parseLocalDateTime(filters.get("dueDateFrom"));
            spec = spec.and(InvoiceSpecification.dueDateBetween(from, null));
        }

        if (StringUtils.hasLength(filters.get("dueDateTo"))) {
            LocalDateTime to = parseLocalDateTime(filters.get("dueDateTo"));
            spec = spec.and(InvoiceSpecification.dueDateBetween(null, to));
        }

        return spec;
    }

    private BigDecimal parseBigDecimal(Map<String, String> filters, String key) {
        String value = filters.get(key);
        if (!StringUtils.hasLength(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid numeric value for filter '{}': {}", key, value);
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (!StringUtils.hasLength(value)) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            log.warn("Invalid date format for filter: {}", value);
            return null;
        }
    }

    private String generateInvoiceNumber() {
        return "INV-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private InvoiceDto convertToDto(Invoice invoice) {
        return InvoiceDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .userId(invoice.getUserId())
                .transactionId(invoice.getTransactionId())
                .amount(invoice.getAmount())
                .currency(invoice.getCurrency())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .status(invoice.getStatus())
                .pdfUrl(invoice.getPdfUrl())
                .issueDate(invoice.getIssueDate())
                .dueDate(invoice.getDueDate())
                .paidAt(invoice.getPaidAt())
                .createdAt(invoice.getCreatedAt())
                .build();
    }

    private Invoice convertToEntity(InvoiceDto dto) {
        return Invoice.builder()
                .id(dto.getId())
                .invoiceNumber(dto.getInvoiceNumber())
                .userId(dto.getUserId())
                .transactionId(dto.getTransactionId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .taxAmount(dto.getTaxAmount())
                .totalAmount(dto.getTotalAmount())
                .status(dto.getStatus())
                .pdfUrl(dto.getPdfUrl())
                .issueDate(dto.getIssueDate())
                .dueDate(dto.getDueDate())
                .paidAt(dto.getPaidAt())
                .createdAt(dto.getCreatedAt())
                .build();
    }

    @Override
    public byte[] exportInvoicesToPdf(PaginationAndFilteringDto paginationDto) {
        log.info("📄 Exporting invoices to PDF with pagination: page={}, size={}",
                paginationDto.getPage(), paginationDto.getSize());

        // Use large page size for export (500 records per page)
        PaginationAndFilteringDto exportDto = new PaginationAndFilteringDto();
        exportDto.setPage(1);
        exportDto.setSize(500);
        exportDto.setSortField(paginationDto.getSortField());
        exportDto.setSortDirection(paginationDto.getSortDirection());
        exportDto.setFilters(paginationDto.getFilters());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate());

        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();

            // Add title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Invoices Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            document.add(new Paragraph(" "));

            // Create table
            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{10, 20, 15, 15, 15, 15, 20, 15, 15});

            // Add headers
            addTableHeader(table, "ID");
            addTableHeader(table, "Invoice #");
            addTableHeader(table, "User ID");
            addTableHeader(table, "Amount");
            addTableHeader(table, "Tax");
            addTableHeader(table, "Total");
            addTableHeader(table, "Status");
            addTableHeader(table, "Issue Date");
            addTableHeader(table, "Due Date");

            // Fetch and add data with pagination
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                exportDto.setPage(page);
                Pageable pageable = PaginationUtil.createPageRequest(exportDto);
                Specification<Invoice> specification = buildSpecificationFromFilters(paginationDto.getFilters());
                Page<Invoice> invoicePage = invoiceRepository.findAll(specification, pageable);

                for (Invoice invoice : invoicePage.getContent()) {
                    addTableRow(table, invoice);
                }

                hasMore = invoicePage.hasNext();
                page++;
            }

            document.add(table);
            document.close();

            log.info("✅ PDF export completed. Size: {} bytes", baos.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("❌ Failed to generate PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    @Override
    public byte[] exportInvoicesToCsv(PaginationAndFilteringDto paginationDto) {
        log.info("📄 Exporting invoices to CSV with pagination: page={}, size={}",
                paginationDto.getPage(), paginationDto.getSize());

        PaginationAndFilteringDto exportDto = new PaginationAndFilteringDto();
        exportDto.setPage(1);
        exportDto.setSize(500);
        exportDto.setSortField(paginationDto.getSortField());
        exportDto.setSortDirection(paginationDto.getSortDirection());
        exportDto.setFilters(paginationDto.getFilters());

        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter);

        // Write headers
        String[] headers = {
                "ID", "Invoice Number", "User ID", "Transaction ID",
                "Amount", "Currency", "Tax Amount", "Total Amount",
                "Status", "Issue Date", "Due Date", "Paid At", "Created At"
        };
        csvWriter.writeNext(headers);

        // Fetch and write data with pagination
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            exportDto.setPage(page);
            Pageable pageable = PaginationUtil.createPageRequest(exportDto);
            Specification<Invoice> specification = buildSpecificationFromFilters(paginationDto.getFilters());
            Page<Invoice> invoicePage = invoiceRepository.findAll(specification, pageable);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (Invoice invoice : invoicePage.getContent()) {
                String[] row = {
                        String.valueOf(invoice.getId()),
                        invoice.getInvoiceNumber(),
                        String.valueOf(invoice.getUserId()),
                        String.valueOf(invoice.getTransactionId()),
                        invoice.getAmount().toString(),
                        invoice.getCurrency(),
                        invoice.getTaxAmount() != null ? invoice.getTaxAmount().toString() : "",
                        invoice.getTotalAmount().toString(),
                        invoice.getStatus().name(),
                        invoice.getIssueDate() != null ? invoice.getIssueDate().format(formatter) : "",
                        invoice.getDueDate() != null ? invoice.getDueDate().format(formatter) : "",
                        invoice.getPaidAt() != null ? invoice.getPaidAt().format(formatter) : "",
                        invoice.getCreatedAt() != null ? invoice.getCreatedAt().format(formatter) : ""
                };
                csvWriter.writeNext(row);
            }

            hasMore = invoicePage.hasNext();
            page++;
        }

        try {
            csvWriter.close();
            log.info("✅ CSV export completed");
            return stringWriter.toString().getBytes();
        } catch (Exception e) {
            log.error("❌ Failed to generate CSV: {}", e.getMessage());
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }

    @Override
    public byte[] exportUserInvoicesToPdf(Long userId, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Exporting invoices for user {} to PDF", userId);

        PaginationAndFilteringDto exportDto = new PaginationAndFilteringDto();
        exportDto.setPage(1);
        exportDto.setSize(500);
        exportDto.setSortField(paginationDto.getSortField());
        exportDto.setSortDirection(paginationDto.getSortDirection());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate());

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph title = new Paragraph("Invoice Statement - User ID: " + userId, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{10, 20, 15, 15, 15, 20, 15, 15});

            addTableHeader(table, "ID");
            addTableHeader(table, "Invoice #");
            addTableHeader(table, "Amount");
            addTableHeader(table, "Tax");
            addTableHeader(table, "Total");
            addTableHeader(table, "Status");
            addTableHeader(table, "Issue Date");
            addTableHeader(table, "Due Date");

            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                exportDto.setPage(page);
                Pageable pageable = PaginationUtil.createPageRequest(exportDto);
                Page<Invoice> invoicePage = invoiceRepository.findAllByUserId(userId, pageable);

                for (Invoice invoice : invoicePage.getContent()) {
                    addTableRow(table, invoice);
                }

                hasMore = invoicePage.hasNext();
                page++;
            }

            document.add(table);
            document.close();

            return baos.toByteArray();

        } catch (Exception e) {
            log.error("❌ Failed to generate user PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    @Override
    public byte[] exportUserInvoicesToCsv(Long userId, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Exporting invoices for user {} to CSV", userId);

        PaginationAndFilteringDto exportDto = new PaginationAndFilteringDto();
        exportDto.setPage(1);
        exportDto.setSize(500);
        exportDto.setSortField(paginationDto.getSortField());
        exportDto.setSortDirection(paginationDto.getSortDirection());

        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter);

        String[] headers = {
                "ID", "Invoice Number", "Amount", "Currency",
                "Tax Amount", "Total Amount", "Status", "Issue Date", "Due Date", "Paid At"
        };
        csvWriter.writeNext(headers);

        int page = 1;
        boolean hasMore = true;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        while (hasMore) {
            exportDto.setPage(page);
            Pageable pageable = PaginationUtil.createPageRequest(exportDto);
            Page<Invoice> invoicePage = invoiceRepository.findAllByUserId(userId, pageable);

            for (Invoice invoice : invoicePage.getContent()) {
                String[] row = {
                        String.valueOf(invoice.getId()),
                        invoice.getInvoiceNumber(),
                        invoice.getAmount().toString(),
                        invoice.getCurrency(),
                        invoice.getTaxAmount() != null ? invoice.getTaxAmount().toString() : "",
                        invoice.getTotalAmount().toString(),
                        invoice.getStatus().name(),
                        invoice.getIssueDate() != null ? invoice.getIssueDate().format(formatter) : "",
                        invoice.getDueDate() != null ? invoice.getDueDate().format(formatter) : "",
                        invoice.getPaidAt() != null ? invoice.getPaidAt().format(formatter) : ""
                };
                csvWriter.writeNext(row);
            }

            hasMore = invoicePage.hasNext();
            page++;
        }

        try {
            csvWriter.close();
            return stringWriter.toString().getBytes();
        } catch (Exception e) {
            log.error("❌ Failed to generate user CSV: {}", e.getMessage());
            throw new RuntimeException("Failed to generate CSV", e);
        }
    }

    private void addTableHeader(PdfPTable table, String headerText) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        PdfPCell cell = new PdfPCell(new Phrase(headerText, font));
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addTableRow(PdfPTable table, Invoice invoice) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        table.addCell(String.valueOf(invoice.getId()));
        table.addCell(invoice.getInvoiceNumber());
        table.addCell(String.valueOf(invoice.getUserId()));
        table.addCell(invoice.getAmount().toString());
        table.addCell(invoice.getTaxAmount() != null ? invoice.getTaxAmount().toString() : "-");
        table.addCell(invoice.getTotalAmount().toString());
        table.addCell(invoice.getStatus().name());
        table.addCell(invoice.getIssueDate() != null ? invoice.getIssueDate().format(formatter) : "-");
        table.addCell(invoice.getDueDate() != null ? invoice.getDueDate().format(formatter) : "-");
    }


}