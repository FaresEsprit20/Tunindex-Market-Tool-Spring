package com.tunindex.market_tool.payment.service.payment_transaction;

import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationUtil;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.PaymentRequestDto;
import com.tunindex.market_tool.payment.dto.PaymentResponseDto;
import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.enums.PaymentMethod;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import com.tunindex.market_tool.payment.repository.PaymentTransactionRepository;
import com.tunindex.market_tool.payment.specifications.PaymentTransactionSpecification;
import com.tunindex.market_tool.payment.validators.PaymentTransactionValidator;
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
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;

    @Override
    @Transactional(readOnly = true)
    public PaymentResponseDto findById(Long id) {
        log.info("🔍 Finding payment transaction by id: {}", id);

        List<String> errors = new ArrayList<>();

        if (id == null || id <= 0) {
            errors.add("Transaction ID must be a positive number");
            throw new InvalidEntityException("Invalid transaction ID",
                    ErrorCodes.PAYMENT_NOT_FOUND, errors);
        }

        return paymentTransactionRepository.findById(id)
                .map(this::convertToResponseDto)
                .orElseThrow(() -> {
                    errors.add("No payment transaction found with id: " + id);
                    return new EntityNotFoundException(
                            "Payment transaction not found with id: " + id,
                            ErrorCodes.PAYMENT_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponseDto findByTransactionId(String transactionId) {
        log.info("🔍 Finding payment transaction by transactionId: {}", transactionId);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(transactionId)) {
            errors.add("Transaction ID cannot be empty");
            throw new InvalidEntityException("Invalid transaction ID", ErrorCodes.PAYMENT_NOT_FOUND, errors);
        }

        return paymentTransactionRepository.findByTransactionId(transactionId)
                .map(this::convertToResponseDto)
                .orElseThrow(() -> {
                    errors.add("No payment transaction found with transactionId: " + transactionId);
                    return new EntityNotFoundException(
                            "Payment transaction not found with transactionId: " + transactionId,
                            ErrorCodes.PAYMENT_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponseDto findByProviderPaymentId(String providerPaymentId) {
        log.info("🔍 Finding payment transaction by providerPaymentId: {}", providerPaymentId);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(providerPaymentId)) {
            errors.add("Provider payment ID cannot be empty");
            throw new InvalidEntityException("Invalid provider payment ID", ErrorCodes.PAYMENT_NOT_FOUND, errors);
        }

        return paymentTransactionRepository.findByProviderPaymentId(providerPaymentId)
                .map(this::convertToResponseDto)
                .orElseThrow(() -> {
                    errors.add("No payment transaction found with providerPaymentId: " + providerPaymentId);
                    return new EntityNotFoundException(
                            "Payment transaction not found with providerPaymentId: " + providerPaymentId,
                            ErrorCodes.PAYMENT_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponseDto> findAllByUserId(Long userId, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all payment transactions for user: {} with pagination", userId);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<PaymentTransaction> transactionPage = paymentTransactionRepository.findAllByUserId(userId, pageable);

        return buildPagedResponse(transactionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponseDto> findAllByStatus(PaymentStatus status, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all payment transactions with status: {}", status);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<PaymentTransaction> transactionPage = paymentTransactionRepository.findAllByStatus(status, pageable);

        return buildPagedResponse(transactionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponseDto> findByUserIdAndStatus(Long userId, PaymentStatus status, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding payment transactions for user: {} with status: {}", userId, status);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<PaymentTransaction> transactionPage = paymentTransactionRepository.findByUserIdAndStatus(userId, status, pageable);

        return buildPagedResponse(transactionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponseDto> filterTransactions(PaginationAndFilteringDto paginationDto) {
        log.info("🔍 Filtering payment transactions with pagination: page={}, size={}, filters={}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        validatePaginationDto(paginationDto);

        Specification<PaymentTransaction> specification = buildSpecificationFromFilters(paginationDto.getFilters());
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<PaymentTransaction> transactionPage = paymentTransactionRepository.findAll(specification, pageable);

        return buildPagedResponse(transactionPage);
    }

    @Override
    @Transactional
    public PaymentResponseDto initiatePayment(PaymentRequestDto paymentRequest) {
        log.info("💰 Initiating payment for user: {}", paymentRequest.getUserId());

        // Validate the payment request
        PaymentTransactionValidator.validate(paymentRequest);

        // Generate unique transaction ID
        String transactionId = generateTransactionId();

        // Create payment transaction
        PaymentTransaction transaction = PaymentTransaction.builder()
                .transactionId(transactionId)
                .userId(paymentRequest.getUserId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .paymentMethod(paymentRequest.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .description("Payment for subscription plan: " + paymentRequest.getPlanId())
                .subscriptionId(paymentRequest.getPlanId())
                .createdAt(LocalDateTime.now())
                .paymentDate(LocalDateTime.now())
                .build();

        PaymentTransaction savedTransaction = paymentTransactionRepository.save(transaction);

        log.info("✅ Payment initiated successfully with transaction ID: {}", transactionId);
        return convertToResponseDto(savedTransaction);
    }

    @Override
    @Transactional
    public PaymentResponseDto updateTransactionStatus(String transactionId, PaymentStatus newStatus) {
        log.info("🔄 Updating transaction status for: {} to: {}", transactionId, newStatus);

        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment transaction not found with transactionId: " + transactionId,
                        ErrorCodes.PAYMENT_NOT_FOUND,
                        List.of("No transaction found")
                ));

        PaymentTransactionValidator.validateStatusTransition(transaction.getStatus(), newStatus);
        transaction.setStatus(newStatus);

        PaymentTransaction updatedTransaction = paymentTransactionRepository.save(transaction);
        log.info("✅ Transaction status updated successfully");
        return convertToResponseDto(updatedTransaction);
    }

    @Override
    @Transactional
    public PaymentResponseDto updateTransactionStatusWithReason(String transactionId, PaymentStatus newStatus, String reason) {
        log.info("🔄 Updating transaction status for: {} to: {} with reason: {}", transactionId, newStatus, reason);

        PaymentTransaction transaction = paymentTransactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payment transaction not found with transactionId: " + transactionId,
                        ErrorCodes.PAYMENT_NOT_FOUND,
                        List.of("No transaction found")
                ));

        PaymentTransactionValidator.validateStatusTransition(transaction.getStatus(), newStatus);
        transaction.setStatus(newStatus);
        transaction.setFailureReason(reason);

        PaymentTransaction updatedTransaction = paymentTransactionRepository.save(transaction);
        log.info("✅ Transaction status updated with reason");
        return convertToResponseDto(updatedTransaction);
    }

    @Override
    @Transactional
    public PaymentResponseDto markAsCompleted(String transactionId) {
        return updateTransactionStatus(transactionId, PaymentStatus.COMPLETED);
    }

    @Override
    @Transactional
    public PaymentResponseDto markAsFailed(String transactionId, String reason) {
        return updateTransactionStatusWithReason(transactionId, PaymentStatus.FAILED, reason);
    }

    @Override
    @Transactional
    public PaymentResponseDto markAsRefunded(String transactionId) {
        return updateTransactionStatus(transactionId, PaymentStatus.REFUNDED);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalAmountSpentByUser(Long userId, PaymentStatus status) {
        log.info("💰 Getting total amount spent by user: {} with status: {}", userId, status);
        return paymentTransactionRepository.getTotalAmountSpentByUser(userId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSuccessfulPaymentsByUser(Long userId) {
        log.info("📊 Counting successful payments for user: {}", userId);
        return paymentTransactionRepository.countSuccessfulPaymentsByUser(userId, PaymentStatus.COMPLETED);
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

    private PagedResponse<PaymentResponseDto> buildPagedResponse(Page<PaymentTransaction> transactionPage) {
        List<PaymentResponseDto> content = transactionPage.getContent()
                .stream()
                .map(this::convertToResponseDto)
                .toList();

        return new PagedResponse<>(
                content,
                transactionPage.getNumber() + 1,
                transactionPage.getSize(),
                transactionPage.getTotalElements(),
                transactionPage.getTotalPages()
        );
    }

    private Specification<PaymentTransaction> buildSpecificationFromFilters(Map<String, String> filters) {
        Specification<PaymentTransaction> spec = PaymentTransactionSpecification.empty();

        if (filters == null || filters.isEmpty()) {
            return spec;
        }

        if (StringUtils.hasLength(filters.get("userId"))) {
            try {
                Long userId = Long.valueOf(filters.get("userId"));
                spec = spec.and(PaymentTransactionSpecification.userIdEquals(userId));
            } catch (NumberFormatException e) {
                log.warn("Invalid userId value: {}", filters.get("userId"));
            }
        }

        if (StringUtils.hasLength(filters.get("transactionId"))) {
            spec = spec.and(PaymentTransactionSpecification.transactionIdContains(filters.get("transactionId")));
        }

        if (StringUtils.hasLength(filters.get("status"))) {
            try {
                PaymentStatus status = PaymentStatus.valueOf(filters.get("status").toUpperCase());
                spec = spec.and(PaymentTransactionSpecification.statusEquals(status));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", filters.get("status"));
            }
        }

        if (StringUtils.hasLength(filters.get("paymentMethod"))) {
            try {
                PaymentMethod method = PaymentMethod.valueOf(filters.get("paymentMethod").toUpperCase());
                spec = spec.and(PaymentTransactionSpecification.paymentMethodEquals(method));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid paymentMethod value: {}", filters.get("paymentMethod"));
            }
        }

        if (StringUtils.hasLength(filters.get("currency"))) {
            spec = spec.and(PaymentTransactionSpecification.currencyEquals(filters.get("currency")));
        }

        if (StringUtils.hasLength(filters.get("providerName"))) {
            spec = spec.and(PaymentTransactionSpecification.providerNameEquals(filters.get("providerName")));
        }

        // Amount range filters
        if (filters.containsKey("minAmount") || filters.containsKey("maxAmount")) {
            BigDecimal minAmount = parseBigDecimal(filters, "minAmount");
            BigDecimal maxAmount = parseBigDecimal(filters, "maxAmount");
            spec = spec.and(PaymentTransactionSpecification.amountBetween(minAmount, maxAmount));
        }

        // Date filters
        if (StringUtils.hasLength(filters.get("paymentDateFrom"))) {
            LocalDateTime from = parseLocalDateTime(filters.get("paymentDateFrom"));
            spec = spec.and(PaymentTransactionSpecification.paymentDateBetween(from, null));
        }

        if (StringUtils.hasLength(filters.get("paymentDateTo"))) {
            LocalDateTime to = parseLocalDateTime(filters.get("paymentDateTo"));
            spec = spec.and(PaymentTransactionSpecification.paymentDateBetween(null, to));
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

    private String generateTransactionId() {
        return "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private PaymentResponseDto convertToResponseDto(PaymentTransaction transaction) {
        return PaymentResponseDto.builder()
                .transactionId(transaction.getId())
                .transactionReference(transaction.getTransactionId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .status(transaction.getStatus())
                .paymentUrl(generatePaymentUrl(transaction.getTransactionId()))
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private String generatePaymentUrl(String transactionId) {
        // This would be replaced with actual payment gateway URL
        return "/api/payments/" + transactionId + "/process";
    }
}