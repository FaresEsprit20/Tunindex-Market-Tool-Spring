package com.tunindex.market_tool.payment.validators;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.RefundRequestDto;
import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.Refund;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RefundValidator {

    public static void validate(RefundRequestDto request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Refund request cannot be null");
            throw new InvalidEntityException("Refund request is null", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }

        // Validate transaction ID
        if (request.getTransactionId() == null || request.getTransactionId() <= 0) {
            errors.add("Transaction ID must be a positive number");
        }

        // Validate amount
        if (request.getAmount() == null) {
            errors.add("Refund amount must not be null");
        }
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Refund amount must be greater than 0");
        }

        // Validate reason (optional but if provided, max length)
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
            errors.add("Transaction has already been fully refunded");
        }

        // Check refund amount
        if (refundAmount != null && refundAmount.compareTo(transaction.getAmount()) > 0) {
            errors.add("Refund amount cannot exceed transaction amount");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Cannot process refund for this transaction", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }
    }

    public static void validateRefundStatus(Refund refund, RefundStatus newStatus) {
        List<String> errors = new ArrayList<>();

        if (refund == null) {
            errors.add("Refund record not found");
            throw new InvalidEntityException("Refund not found", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }

        if (refund.getStatus() == RefundStatus.COMPLETED) {
            errors.add("Cannot change status of a completed refund");
        }

        if (refund.getStatus() == RefundStatus.FAILED && newStatus == RefundStatus.COMPLETED) {
            errors.add("Cannot change failed refund to completed");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid refund status transition", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }
    }

}