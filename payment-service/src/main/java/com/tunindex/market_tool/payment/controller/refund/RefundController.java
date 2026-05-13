package com.tunindex.market_tool.payment.controller.refund;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.RefundRequestDto;
import com.tunindex.market_tool.payment.dto.RefundResponseDto;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
import com.tunindex.market_tool.payment.service.refund.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RefundController implements RefundApi {

    private final RefundService refundService;

    @Override
    public ResponseEntity<RefundResponseDto> getRefundById(Long id) {
        log.info("GET /api/refunds/{}", id);
        RefundResponseDto refund = refundService.findById(id);
        return ResponseEntity.ok(refund);
    }

    @Override
    public ResponseEntity<PagedResponse<RefundResponseDto>> getRefundsByTransactionId(
            Long transactionId, int page, int size, String sortField, String sortDirection) {

        log.info("GET /api/refunds/transaction/{} - page: {}, size: {}, sortField: {}, sortDirection: {}",
                transactionId, page, size, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        PagedResponse<RefundResponseDto> response = refundService.findAllByTransactionId(transactionId, paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<RefundResponseDto>> getRefundsByStatus(
            RefundStatus status, int page, int size, String sortField, String sortDirection) {

        log.info("GET /api/refunds/status/{} - page: {}, size: {}, sortField: {}, sortDirection: {}",
                status, page, size, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        PagedResponse<RefundResponseDto> response = refundService.findAllByStatus(status, paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RefundResponseDto> requestRefund(RefundRequestDto refundRequest) {
        log.info("POST /api/refunds/request - Transaction: {}, Amount: {}",
                refundRequest.getTransactionId(), refundRequest.getAmount());

        RefundResponseDto response = refundService.requestRefund(refundRequest);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<RefundResponseDto>> filterRefunds(PaginationAndFilteringDto paginationDto) {
        log.info("POST /api/refunds/filter - page: {}, size: {}, filters: {}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        PagedResponse<RefundResponseDto> response = refundService.filterRefunds(paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RefundResponseDto> updateRefundStatus(Long refundId, RefundStatus status) {
        log.info("PUT /api/refunds/{}/status - New status: {}", refundId, status);

        RefundResponseDto response = refundService.updateRefundStatus(refundId, status);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RefundResponseDto> markRefundAsCompleted(Long refundId) {
        log.info("PUT /api/refunds/{}/complete", refundId);

        RefundResponseDto response = refundService.markAsCompleted(refundId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<RefundResponseDto> markRefundAsFailed(Long refundId, String reason) {
        log.info("PUT /api/refunds/{}/fail - Reason: {}", refundId, reason);

        RefundResponseDto response = refundService.markAsFailed(refundId, reason);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<BigDecimal> getTotalRefundedAmount(Long transactionId) {
        log.info("GET /api/refunds/statistics/total-refunded/{}", transactionId);

        BigDecimal totalAmount = refundService.getTotalRefundedAmountForTransaction(transactionId);
        return ResponseEntity.ok(totalAmount);
    }

    @Override
    public ResponseEntity<Boolean> hasExistingRefund(Long transactionId) {
        log.info("GET /api/refunds/statistics/exists/{}", transactionId);

        boolean exists = refundService.hasExistingRefund(transactionId);
        return ResponseEntity.ok(exists);
    }

    
}