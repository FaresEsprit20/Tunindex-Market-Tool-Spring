package com.tunindex.market_tool.payment.validators.gateway;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.gateway.PaymentGatewayStatusRequest;

import java.util.ArrayList;
import java.util.List;

public class PaymentGatewayStatusRequestValidator {

    public static void validate(PaymentGatewayStatusRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Gateway status request cannot be null");
            throw new InvalidEntityException("Gateway status request is null", ErrorCodes.PAYMENT_GATEWAY_ERROR, errors);
        }

        // Validate provider payment ID
        if (request.getProviderPaymentId() == null || request.getProviderPaymentId().trim().isEmpty()) {
            errors.add("Provider payment ID must not be null or empty");
        }

        // Validate transaction ID
        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            errors.add("Transaction ID must not be null or empty");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid gateway status request", ErrorCodes.PAYMENT_GATEWAY_ERROR, errors);
        }
    }
}