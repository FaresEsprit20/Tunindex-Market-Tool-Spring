package com.tunindex.market_tool.payment.validators.gateway;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.PaymentStatusRequestDto;

import java.util.ArrayList;
import java.util.List;

public class PaymentStatusRequestValidator {

    public static void validate(PaymentStatusRequestDto request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Payment status request cannot be null");
            throw new InvalidEntityException("Payment status request is null", ErrorCodes.PAYMENT_TRANSACTION_NOT_FOUND, errors);
        }

        // Validate transaction ID
        if (request.getTransactionId() == null || request.getTransactionId().trim().isEmpty()) {
            errors.add("Transaction ID must not be null or empty");
        }

        // Validate provider payment ID
        if (request.getProviderPaymentId() == null || request.getProviderPaymentId().trim().isEmpty()) {
            errors.add("Provider payment ID must not be null or empty");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid payment status request", ErrorCodes.PAYMENT_TRANSACTION_NOT_FOUND, errors);
        }
    }

}