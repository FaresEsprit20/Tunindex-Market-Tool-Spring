package com.tunindex.market_tool.payment.validators;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.RefundPaymentRequestDto;

import java.util.ArrayList;
import java.util.List;

public class RefundPaymentValidator {

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

        // Validate reason (optional)
        if (request.getReason() != null && request.getReason().length() > 500) {
            errors.add("Refund reason must not exceed 500 characters");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid refund request", ErrorCodes.PAYMENT_REFUND_FAILED, errors);
        }
    }
}