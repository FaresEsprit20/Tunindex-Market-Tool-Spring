package com.tunindex.market_tool.payment.service.refund;

import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationUtil;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.RefundRequestDto;
import com.tunindex.market_tool.payment.dto.RefundResponseDto;
import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.Refund;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
import com.tunindex.market_tool.payment.repository.PaymentTransactionRepository;
import com.tunindex.market_tool.payment.repository.RefundRepository;
import com.tunindex.market_tool.payment.specifications.RefundSpecification;
import com.tunindex.market_tool.payment.validators.RefundValidator;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public RefundResponseDto findById(Long id) {
        log.info("🔍 Finding refund by id: {}", id);

        List<String> errors = new ArrayList<>();

        if (id == null || id <= 0) {
            errors.add("Refund ID must be a positive number");
            throw new InvalidEntityException("Invalid refund ID", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }

        return refundRepository.findById(id)
                .map(this::convertToResponseDto)
                .orElseThrow(() -> {
                    errors.add("No refund found with id: " + id);
                    return new EntityNotFoundException(
                            "Refund not found with id: " + id,
                            ErrorCodes.PAYMENT_REFUND_FAILED,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponseDto findByProviderRefundId(String providerRefundId) {
        log.info("🔍 Finding refund by providerRefundId: {}", providerRefundId);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(providerRefundId)) {
            errors.add("Provider refund ID cannot be empty");
            throw new InvalidEntityException("Invalid provider refund ID", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }

        return refundRepository.findByProviderRefundId(providerRefundId)
                .map(this::convertToResponseDto)
                .orElseThrow(() -> {
                    errors.add("No refund found with providerRefundId: " + providerRefundId);
                    return new EntityNotFoundException(
                            "Refund not found with providerRefundId: " + providerRefundId,
                            ErrorCodes.PAYMENT_REFUND_FAILED,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<RefundResponseDto> findAllByTransactionId(Long transactionId, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all refunds for transaction: {} with pagination", transactionId);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Refund> refundPage = refundRepository.findAllByTransactionId(transactionId, pageable);

        return buildPagedResponse(refundPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<RefundResponseDto> findAllByStatus(RefundStatus status, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all refunds with status: {}", status);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Refund> refundPage = refundRepository.findAllByStatus(status, pageable);

        return buildPagedResponse(refundPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<RefundResponseDto> filterRefunds(PaginationAndFilteringDto paginationDto) {
        log.info("🔍 Filtering refunds with pagination: page={}, size={}, filters={}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        validatePaginationDto(paginationDto);

        Specification<Refund> specification = buildSpecificationFromFilters(paginationDto.getFilters());
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<Refund> refundPage = refundRepository.findAll(specification, pageable);

        return buildPagedResponse(refundPage);
    }

    @Override
    @Transactional
    public RefundResponseDto requestRefund(RefundRequestDto refundRequest) {
        log.info("💰 Requesting refund for transaction: {}", refundRequest.getTransactionId());

        // Validate the refund request
        RefundValidator.validate(refundRequest);

        // Get the original transaction
        PaymentTransaction transaction = paymentTransactionRepository.findById(refundRequest.getTransactionId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment transaction not found with id: " + refundRequest.getTransactionId(),
                        ErrorCodes.PAYMENT_NOT_FOUND,
                        List.of("No transaction found")
                ));

        // Validate transaction for refund
        RefundValidator.validateForTransaction(transaction, refundRequest.getAmount());

        // Check if refund already exists
        if (refundRepository.existsByTransactionIdAndStatus(transaction.getId(), RefundStatus.COMPLETED)) {
            throw new InvalidEntityException(
                    "A refund has already been processed for this transaction",
                    ErrorCodes.PAYMENT_REFUND_ALREADY_PROCESSED,
                    List.of("Refund already exists")
            );
        }

        // Generate refund ID
        String refundId = generateRefundId();

        // Create refund record
        Refund refund = Refund.builder()
                .transactionId(refundRequest.getTransactionId())
                .amount(refundRequest.getAmount())
                .reason(refundRequest.getReason())
                .status(RefundStatus.PENDING)
                .refundDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        Refund savedRefund = refundRepository.save(refund);

        log.info("✅ Refund requested successfully with ID: {}", refundId);
        return convertToResponseDto(savedRefund);
    }

    @Override
    @Transactional
    public RefundResponseDto updateRefundStatus(Long refundId, RefundStatus newStatus) {
        log.info("🔄 Updating refund status for id: {} to: {}", refundId, newStatus);

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Refund not found with id: " + refundId,
                        ErrorCodes.PAYMENT_REFUND_FAILED,
                        List.of("No refund found")
                ));

        RefundValidator.validateRefundStatus(refund, newStatus);
        refund.setStatus(newStatus);

        // If refund is completed, update the original transaction status
        if (newStatus == RefundStatus.COMPLETED) {
            updateTransactionRefundStatus(refund.getTransactionId());
        }

        Refund updatedRefund = refundRepository.save(refund);
        log.info("✅ Refund status updated successfully");
        return convertToResponseDto(updatedRefund);
    }

    @Override
    @Transactional
    public RefundResponseDto updateRefundWithProviderId(Long refundId, RefundStatus status, String providerRefundId) {
        log.info("🔄 Updating refund with provider ID: {} for refund id: {}", providerRefundId, refundId);

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Refund not found with id: " + refundId,
                        ErrorCodes.PAYMENT_REFUND_FAILED,
                        List.of("No refund found")
                ));

        refund.setStatus(status);
        refund.setProviderRefundId(providerRefundId);

        if (status == RefundStatus.COMPLETED) {
            updateTransactionRefundStatus(refund.getTransactionId());
        }

        Refund updatedRefund = refundRepository.save(refund);
        log.info("✅ Refund updated with provider ID");
        return convertToResponseDto(updatedRefund);
    }

    @Override
    @Transactional
    public RefundResponseDto markAsCompleted(Long refundId) {
        return updateRefundStatus(refundId, RefundStatus.COMPLETED);
    }

    @Override
    @Transactional
    public RefundResponseDto markAsFailed(Long refundId, String failureReason) {
        log.info("❌ Marking refund as failed for id: {} with reason: {}", refundId, failureReason);

        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Refund not found with id: " + refundId,
                        ErrorCodes.PAYMENT_REFUND_FAILED,
                        List.of("No refund found")
                ));

        refund.setStatus(RefundStatus.FAILED);
        refund.setFailureReason(failureReason);

        Refund updatedRefund = refundRepository.save(refund);
        log.info("✅ Refund marked as failed");
        return convertToResponseDto(updatedRefund);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalRefundedAmountForTransaction(Long transactionId) {
        log.info("💰 Getting total refunded amount for transaction: {}", transactionId);
        return refundRepository.getTotalRefundedAmountForTransaction(transactionId, RefundStatus.COMPLETED);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasExistingRefund(Long transactionId) {
        log.info("🔍 Checking existing refund for transaction: {}", transactionId);
        return refundRepository.existsByTransactionIdAndStatus(transactionId, RefundStatus.COMPLETED);
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

    private PagedResponse<RefundResponseDto> buildPagedResponse(Page<Refund> refundPage) {
        List<RefundResponseDto> content = refundPage.getContent()
                .stream()
                .map(this::convertToResponseDto)
                .toList();

        return new PagedResponse<>(
                content,
                refundPage.getNumber() + 1,
                refundPage.getSize(),
                refundPage.getTotalElements(),
                refundPage.getTotalPages()
        );
    }

    private Specification<Refund> buildSpecificationFromFilters(Map<String, String> filters) {
        Specification<Refund> spec = RefundSpecification.empty();

        if (filters == null || filters.isEmpty()) {
            return spec;
        }

        if (StringUtils.hasLength(filters.get("transactionId"))) {
            try {
                Long transactionId = Long.valueOf(filters.get("transactionId"));
                spec = spec.and(RefundSpecification.transactionIdEquals(transactionId));
            } catch (NumberFormatException e) {
                log.warn("Invalid transactionId value: {}", filters.get("transactionId"));
            }
        }

        if (StringUtils.hasLength(filters.get("status"))) {
            try {
                RefundStatus status = RefundStatus.valueOf(filters.get("status").toUpperCase());
                spec = spec.and(RefundSpecification.statusEquals(status));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", filters.get("status"));
            }
        }

        if (StringUtils.hasLength(filters.get("reason"))) {
            spec = spec.and(RefundSpecification.reasonContains(filters.get("reason")));
        }

        if (StringUtils.hasLength(filters.get("providerRefundId"))) {
            spec = spec.and(RefundSpecification.providerRefundIdEquals(filters.get("providerRefundId")));
        }

        // Amount range filters
        if (filters.containsKey("minAmount") || filters.containsKey("maxAmount")) {
            BigDecimal minAmount = parseBigDecimal(filters, "minAmount");
            BigDecimal maxAmount = parseBigDecimal(filters, "maxAmount");
            spec = spec.and(RefundSpecification.amountBetween(minAmount, maxAmount));
        }

        // Date filters
        if (StringUtils.hasLength(filters.get("refundDateFrom"))) {
            LocalDateTime from = parseLocalDateTime(filters.get("refundDateFrom"));
            spec = spec.and(RefundSpecification.refundDateBetween(from, null));
        }

        if (StringUtils.hasLength(filters.get("refundDateTo"))) {
            LocalDateTime to = parseLocalDateTime(filters.get("refundDateTo"));
            spec = spec.and(RefundSpecification.refundDateBetween(null, to));
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

    private String generateRefundId() {
        return "REF-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void updateTransactionRefundStatus(Long transactionId) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId).orElse(null);
        if (transaction != null && transaction.getStatus() != PaymentStatus.REFUNDED) {
            BigDecimal totalRefunded = refundRepository.getTotalRefundedAmountForTransaction(transactionId, RefundStatus.COMPLETED);
            if (totalRefunded.compareTo(transaction.getAmount()) >= 0) {
                transaction.setStatus(PaymentStatus.REFUNDED);
                paymentTransactionRepository.save(transaction);
                log.info("Transaction {} marked as REFUNDED", transactionId);
            }
        }
    }

    private RefundResponseDto convertToResponseDto(Refund refund) {
        return RefundResponseDto.builder()
                .id(refund.getId())
                .transactionId(refund.getTransactionId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus())
                .providerRefundId(refund.getProviderRefundId())
                .failureReason(refund.getFailureReason())
                .refundDate(refund.getRefundDate())
                .createdAt(refund.getCreatedAt())
                .build();
    }


}