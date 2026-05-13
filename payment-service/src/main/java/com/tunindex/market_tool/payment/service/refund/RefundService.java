package com.tunindex.market_tool.payment.service.refund;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.RefundRequestDto;
import com.tunindex.market_tool.payment.dto.RefundResponseDto;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;

import java.math.BigDecimal;

public interface RefundService {

    RefundResponseDto findById(Long id);

    RefundResponseDto findByProviderRefundId(String providerRefundId);

    PagedResponse<RefundResponseDto> findAllByTransactionId(Long transactionId, PaginationAndFilteringDto paginationDto);

    PagedResponse<RefundResponseDto> findAllByStatus(RefundStatus status, PaginationAndFilteringDto paginationDto);

    PagedResponse<RefundResponseDto> filterRefunds(PaginationAndFilteringDto paginationDto);

    RefundResponseDto requestRefund(RefundRequestDto refundRequest);

    RefundResponseDto updateRefundStatus(Long refundId, RefundStatus newStatus);

    RefundResponseDto updateRefundWithProviderId(Long refundId, RefundStatus status, String providerRefundId);

    RefundResponseDto markAsCompleted(Long refundId);

    RefundResponseDto markAsFailed(Long refundId, String failureReason);

    BigDecimal getTotalRefundedAmountForTransaction(Long transactionId);

    boolean hasExistingRefund(Long transactionId);
}