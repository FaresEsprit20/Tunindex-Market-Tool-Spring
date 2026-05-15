package com.tunindex.market_tool.payment.validators;

import com.tunindex.market_tool.payment.dto.PaymentRequestDto;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;

import java.util.ArrayList;
import java.util.List;

public class PaymentTransactionValidator {

    public static void validate(PaymentRequestDto request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Payment request cannot be null");
            throw new InvalidEntityException("Payment request is null", ErrorCodes.PAYMENT_NOT_FOUND, errors);
        }

        // Validate user ID
        if (request.getUserId() == null || request.getUserId() <= 0) {
            errors.add("User ID must be a positive number");
        }

        // Validate plan ID
        if (request.getPlanId() == null || request.getPlanId() <= 0) {
            errors.add("Plan ID must be a positive number");
        }

        // Validate amount
        if (request.getAmount() == null) {
            errors.add("Amount must not be null");
        }
        if (request.getAmount() != null && request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Amount must be greater than 0");
        }

        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            errors.add("Currency must not be null or empty");
        }
        if (request.getCurrency() != null && !request.getCurrency().matches("^[A-Z]{3}$")) {
            errors.add("Currency must be a valid 3-letter ISO code");
        }

        // Validate payment method
        if (request.getPaymentMethod() == null) {
            errors.add("Payment method must not be null");
        }

        // Validate billing period
        if (request.getBillingPeriod() == null) {
            errors.add("Billing period must not be null");
        }
        if (request.getBillingPeriod() != null && !request.getBillingPeriod().matches("(?i)^(MONTHLY|YEARLY)$")) {
            errors.add("Billing period must be either MONTHLY or YEARLY");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid payment transaction", ErrorCodes.PAYMENT_NOT_FOUND, errors);
        }
    }

    public static void validateStatusTransition(PaymentStatus currentStatus, PaymentStatus newStatus) {
        List<String> errors = new ArrayList<>();

        if (currentStatus == null) {
            errors.add("Current status cannot be null");
        }

        if (newStatus == null) {
            errors.add("New status cannot be null");
        }

        if (currentStatus == PaymentStatus.COMPLETED && newStatus != PaymentStatus.REFUNDED) {
            errors.add("Cannot change status from COMPLETED to " + newStatus + ". Only REFUNDED is allowed");
        }

        if (currentStatus == PaymentStatus.REFUNDED && newStatus != PaymentStatus.REFUNDED) {
            errors.add("Cannot change status from REFUNDED");
        }

        if (currentStatus == PaymentStatus.FAILED && newStatus == PaymentStatus.COMPLETED) {
            errors.add("Cannot change status from FAILED to COMPLETED");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid payment status transition", ErrorCodes.PAYMENT_FAILED, errors);
        }
    }
}