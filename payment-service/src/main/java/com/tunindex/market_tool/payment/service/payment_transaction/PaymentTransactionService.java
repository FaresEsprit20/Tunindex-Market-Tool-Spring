package com.tunindex.market_tool.payment.service.payment_transaction;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.PaymentRequestDto;
import com.tunindex.market_tool.payment.dto.PaymentResponseDto;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;

import java.math.BigDecimal;

public interface PaymentTransactionService {

    PaymentResponseDto findById(Long id);

    PaymentResponseDto findByTransactionId(String transactionId);

    PaymentResponseDto findByProviderPaymentId(String providerPaymentId);

    PagedResponse<PaymentResponseDto> findAllByUserId(Long userId, PaginationAndFilteringDto paginationDto);

    PagedResponse<PaymentResponseDto> findAllByStatus(PaymentStatus status, PaginationAndFilteringDto paginationDto);

    PagedResponse<PaymentResponseDto> findByUserIdAndStatus(Long userId, PaymentStatus status, PaginationAndFilteringDto paginationDto);

    PagedResponse<PaymentResponseDto> filterTransactions(PaginationAndFilteringDto paginationDto);

    PaymentResponseDto initiatePayment(PaymentRequestDto paymentRequest);

    PaymentResponseDto updateTransactionStatus(String transactionId, PaymentStatus newStatus);

    PaymentResponseDto updateTransactionStatusWithReason(String transactionId, PaymentStatus newStatus, String reason);

    PaymentResponseDto markAsCompleted(String transactionId);

    PaymentResponseDto markAsFailed(String transactionId, String reason);

    PaymentResponseDto markAsRefunded(String transactionId);

    BigDecimal getTotalAmountSpentByUser(Long userId, PaymentStatus status);

    long countSuccessfulPaymentsByUser(Long userId);

    // ========== EXPORT METHODS ==========

    byte[] exportTransactionsToPdf(PaginationAndFilteringDto paginationDto);

    byte[] exportTransactionsToCsv(PaginationAndFilteringDto paginationDto);

    byte[] exportUserTransactionsToPdf(Long userId, PaginationAndFilteringDto paginationDto);

    byte[] exportUserTransactionsToCsv(Long userId, PaginationAndFilteringDto paginationDto);

    byte[] exportSingleTransactionToPdf(String transactionId);

    byte[] exportSingleTransactionToCsv(String transactionId);
}