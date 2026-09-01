package com.tunindex.market_tool.payment.validators.gateway;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayRequest;

import java.util.ArrayList;
import java.util.List;

public class PaymentGatewayRequestValidator {

    public static void validate(PaymentGatewayRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Gateway payment request cannot be null");
            throw new InvalidEntityException("Gateway payment request is null", ErrorCodes.PAYMENT_GATEWAY_ERROR, errors);
        }

        // Validate transaction ID
        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            errors.add("Transaction ID must not be null or empty");
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

        // Validate URLs
        if (request.getSuccessUrl() == null || request.getSuccessUrl().trim().isEmpty()) {
            errors.add("Success URL must not be null or empty");
        }

        if (request.getCancelUrl() == null || request.getCancelUrl().trim().isEmpty()) {
            errors.add("Cancel URL must not be null or empty");
        }

        if (request.getWebhookUrl() == null || request.getWebhookUrl().trim().isEmpty()) {
            errors.add("Webhook URL must not be null or empty");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid gateway payment request", ErrorCodes.PAYMENT_GATEWAY_ERROR, errors);
        }
    }


}