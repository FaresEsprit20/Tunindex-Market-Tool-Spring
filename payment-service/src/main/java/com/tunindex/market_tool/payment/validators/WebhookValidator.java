package com.tunindex.market_tool.payment.validators;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.WebhookPayloadDto;

import java.util.ArrayList;
import java.util.List;

public class WebhookValidator {

    public static void validate(WebhookPayloadDto payload, String signature) {
        List<String> errors = new ArrayList<>();

        if (payload == null) {
            errors.add("Webhook payload cannot be null");
            throw new InvalidEntityException("Webhook payload is null", ErrorCodes.KONNECT_WEBHOOK_INVALID, errors);
        }

        // Validate signature
        if (signature == null || signature.trim().isEmpty()) {
            errors.add("Webhook signature must not be null or empty");
        }

        // Validate event type
        if (payload.getEventType() == null || payload.getEventType().trim().isEmpty()) {
            errors.add("Event type must not be null or empty");
        }

        // Validate transaction ID
        if (payload.getTransactionId() == null || payload.getTransactionId().trim().isEmpty()) {
            errors.add("Transaction ID must not be null or empty");
        }

        // Validate provider payment ID
        if (payload.getProviderPaymentId() == null || payload.getProviderPaymentId().trim().isEmpty()) {
            errors.add("Provider payment ID must not be null or empty");
        }

        // Validate amount
        if (payload.getAmount() == null) {
            errors.add("Amount must not be null");
        }
        if (payload.getAmount() != null && payload.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Amount must be greater than 0");
        }

        // Validate currency
        if (payload.getCurrency() == null || payload.getCurrency().trim().isEmpty()) {
            errors.add("Currency must not be null or empty");
        }

        // Validate status
        if (payload.getStatus() == null || payload.getStatus().trim().isEmpty()) {
            errors.add("Status must not be null or empty");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid webhook payload", ErrorCodes.KONNECT_WEBHOOK_INVALID, errors);
        }
    }
}