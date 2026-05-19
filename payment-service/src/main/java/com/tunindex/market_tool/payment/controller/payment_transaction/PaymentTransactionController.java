package com.tunindex.market_tool.payment.controller.payment_transaction;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.PaymentRequestDto;
import com.tunindex.market_tool.payment.dto.PaymentResponseDto;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import com.tunindex.market_tool.payment.service.payment_transaction.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentTransactionController implements PaymentTransactionApi {

    private final PaymentTransactionService paymentTransactionService;

    @Override
    public ResponseEntity<PaymentResponseDto> getTransactionById(Long id) {
        log.info("GET /api/payments/{}", id);
        PaymentResponseDto transaction = paymentTransactionService.findById(id);
        return ResponseEntity.ok(transaction);
    }

    @Override
    public ResponseEntity<PaymentResponseDto> getTransactionByReference(String transactionId) {
        log.info("GET /api/payments/reference/{}", transactionId);
        PaymentResponseDto transaction = paymentTransactionService.findByTransactionId(transactionId);
        return ResponseEntity.ok(transaction);
    }

    @Override
    public ResponseEntity<PagedResponse<PaymentResponseDto>> getTransactionsByUserId(
            Long userId, int page, int size, String sortField, String sortDirection, PaymentStatus status) {

        log.info("GET /api/payments/user/{} - page: {}, size: {}, sortField: {}, sortDirection: {}, status: {}",
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

        PagedResponse<PaymentResponseDto> response = paymentTransactionService.findAllByUserId(userId, paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<PaymentResponseDto>> getTransactionsByStatus(
            PaymentStatus status, int page, int size, String sortField, String sortDirection) {

        log.info("GET /api/payments/status/{} - page: {}, size: {}, sortField: {}, sortDirection: {}",
                status, page, size, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        PagedResponse<PaymentResponseDto> response = paymentTransactionService.findAllByStatus(status, paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentResponseDto> initiatePayment(PaymentRequestDto paymentRequest) {
        log.info("POST /api/payments/initiate - User: {}, Plan: {}, Amount: {}",
                paymentRequest.getUserId(), paymentRequest.getPlanId(), paymentRequest.getAmount());

        PaymentResponseDto response = paymentTransactionService.initiatePayment(paymentRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<PaymentResponseDto>> filterTransactions(PaginationAndFilteringDto paginationDto) {
        log.info("POST /api/payments/filter - page: {}, size: {}, filters: {}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        PagedResponse<PaymentResponseDto> response = paymentTransactionService.filterTransactions(paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentResponseDto> updateTransactionStatus(String transactionId, PaymentStatus status) {
        log.info("PUT /api/payments/{}/status - New status: {}", transactionId, status);

        PaymentResponseDto response = paymentTransactionService.updateTransactionStatus(transactionId, status);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentResponseDto> markAsCompleted(String transactionId) {
        log.info("PUT /api/payments/{}/complete", transactionId);

        PaymentResponseDto response = paymentTransactionService.markAsCompleted(transactionId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentResponseDto> markAsFailed(String transactionId, String reason) {
        log.info("PUT /api/payments/{}/fail - Reason: {}", transactionId, reason);

        PaymentResponseDto response = paymentTransactionService.markAsFailed(transactionId, reason);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<BigDecimal> getTotalAmountSpentByUser(Long userId) {
        log.info("GET /api/payments/statistics/total-spent/{}", userId);

        BigDecimal totalAmount = paymentTransactionService.getTotalAmountSpentByUser(userId, PaymentStatus.COMPLETED);
        return ResponseEntity.ok(totalAmount);
    }

    @Override
    public ResponseEntity<Long> getSuccessfulPaymentsCount(Long userId) {
        log.info("GET /api/payments/statistics/successful-count/{}", userId);

        long count = paymentTransactionService.countSuccessfulPaymentsByUser(userId);
        return ResponseEntity.ok(count);
    }

}