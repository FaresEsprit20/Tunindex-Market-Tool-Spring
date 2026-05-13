package com.tunindex.market_tool.payment.service.invoices;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.InvoiceDto;
import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;

import java.math.BigDecimal;

public interface InvoiceService {

    InvoiceDto findById(Long id);

    InvoiceDto findByInvoiceNumber(String invoiceNumber);

    InvoiceDto findByTransactionId(Long transactionId);

    PagedResponse<InvoiceDto> findAllByUserId(Long userId, PaginationAndFilteringDto paginationDto);

    PagedResponse<InvoiceDto> findAllByStatus(InvoiceStatus status, PaginationAndFilteringDto paginationDto);

    PagedResponse<InvoiceDto> findByUserIdAndStatus(Long userId, InvoiceStatus status, PaginationAndFilteringDto paginationDto);

    PagedResponse<InvoiceDto> findOverdueInvoices(PaginationAndFilteringDto paginationDto);

    PagedResponse<InvoiceDto> filterInvoices(PaginationAndFilteringDto paginationDto);

    InvoiceDto createInvoice(InvoiceDto invoiceDto);

    InvoiceDto updateInvoiceStatus(Long id, InvoiceStatus newStatus);

    InvoiceDto markAsPaid(Long transactionId);

    BigDecimal getTotalInvoicedAmountByUser(Long userId, InvoiceStatus status);

    void deleteInvoice(Long id);
}