package com.tunindex.market_tool.payment.validators;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.RefundPaymentRequestDto;
import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RefundPaymentRequestValidator {

    // Refund time window: 14 days
    private static final int REFUND_DAYS_LIMIT = 14;

    public static void validate(RefundPaymentRequestDto request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Refund request cannot be null");
            throw new InvalidEntityException("Refund request is null", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }

        // Validate transaction ID
        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            errors.add("Transaction ID must not be null or empty");
        }

        // Validate provider payment ID
        if (request.getProviderPaymentId() == null || request.getProviderPaymentId().trim().isEmpty()) {
            errors.add("Provider payment ID must not be null or empty");
        }

        // Validate amount
        if (request.getAmount() == null) {
            errors.add("Refund amount must not be null");
        }
        if (request.getAmount() != null && request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Refund amount must be greater than 0");
        }
        if (request.getAmount() != null && request.getAmount().compareTo(new java.math.BigDecimal("10000")) > 0) {
            errors.add("Refund amount cannot exceed 10,000 TND");
        }

        // Validate reason (optional)
        if (request.getReason() != null && request.getReason().length() > 500) {
            errors.add("Refund reason must not exceed 500 characters");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid refund request", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }
    }

    public static void validateForTransaction(PaymentTransaction transaction, BigDecimal refundAmount) {
        List<String> errors = new ArrayList<>();

        if (transaction == null) {
            errors.add("Transaction not found");
            throw new InvalidEntityException("Transaction not found", ErrorCodes.PAYMENT_NOT_FOUND, errors);
        }

        // Check if transaction can be refunded
        if (transaction.getStatus() != PaymentStatus.COMPLETED) {
            errors.add("Only completed transactions can be refunded");
        }

        // Check if already refunded
        if (transaction.getStatus() == PaymentStatus.REFUNDED) {
            errors.add("Transaction has already been refunded");
        }

        // Check refund amount (must be full amount)
        if (refundAmount != null && refundAmount.compareTo(transaction.getAmount()) != 0) {
            errors.add("Refund must be for the full amount. Partial refunds are not allowed.");
        }

        // Check time window (within 14 days)
        if (transaction.getPaymentDate() != null) {
            LocalDateTime refundDeadline = transaction.getPaymentDate().plusDays(REFUND_DAYS_LIMIT);
            if (LocalDateTime.now().isAfter(refundDeadline)) {
                errors.add("Refund period has expired. Refunds are only allowed within " + REFUND_DAYS_LIMIT + " days of payment.");
            }
        }

        if (!errors.isEmpty()) {
            // Include specific errors in the message
            String message = "Cannot process refund for this transaction: " + String.join("; ", errors);
            throw new InvalidEntityException(message, ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }
    }

}