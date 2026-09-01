package com.tunindex.market_tool.payment.validators;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.CreatePaymentRequestDto;
import com.tunindex.market_tool.payment.dto.PaymentMethodType;

import java.util.ArrayList;
import java.util.List;

public class CreatePaymentValidator {

    public static void validate(CreatePaymentRequestDto request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Payment request cannot be null");
            throw new InvalidEntityException("Payment request is null", ErrorCodes.PAYMENT_GATEWAY_ERROR, errors);
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
        if (request.getAmount() != null && request.getAmount().compareTo(new java.math.BigDecimal("10000")) > 0) {
            errors.add("Amount cannot exceed 10,000 TND");
        }

        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            errors.add("Currency must not be null or empty");
        }
        if (request.getCurrency() != null && !request.getCurrency().matches("^[A-Z]{3}$")) {
            errors.add("Currency must be a valid 3-letter ISO code (TND, USD, EUR)");
        }

        // Validate billing period
        if (request.getBillingPeriod() == null) {
            errors.add("Billing period must not be null");
        }
        if (request.getBillingPeriod() != null && !request.getBillingPeriod().matches("(?i)^(MONTHLY|YEARLY)$")) {
            errors.add("Billing period must be either MONTHLY or YEARLY");
        }

        // Validate customer email
        if (request.getCustomerEmail() == null || request.getCustomerEmail().trim().isEmpty()) {
            errors.add("Customer email must not be null or empty");
        }
        if (request.getCustomerEmail() != null && !request.getCustomerEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            errors.add("Customer email must be a valid email address");
        }

        // Validate customer name
        if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
            errors.add("Customer name must not be null or empty");
        }
        if (request.getCustomerName() != null && (request.getCustomerName().length() < 2 || request.getCustomerName().length() > 100)) {
            errors.add("Customer name must be between 2 and 100 characters");
        }

        // Validate customer phone (optional)
        if (request.getCustomerPhone() != null && !request.getCustomerPhone().trim().isEmpty()) {
            if (!request.getCustomerPhone().matches("^\\+?[0-9]{8,15}$")) {
                errors.add("Customer phone must be a valid phone number (8-15 digits, optionally starting with +)");
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid payment request", ErrorCodes.PAYMENT_INVALID_AMOUNT, errors);
        }
    }
}