package com.tunindex.market_tool.payment.validators.promo;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.dto.promo.CreatePromoCodeRequestDto;
import com.tunindex.market_tool.payment.entities.enums.DiscountType;

import java.util.ArrayList;
import java.util.List;

public class PromoCodeValidator {

    public static void validate(CreatePromoCodeRequestDto request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Promo code request cannot be null");
            throw new InvalidEntityException("Promo code request is null", ErrorCodes.PROMO_CODE_INVALID, errors);
        }

        // Validate code
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            errors.add("Promo code must not be null or empty");
        }
        if (request.getCode() != null && request.getCode().length() > 50) {
            errors.add("Promo code must not exceed 50 characters");
        }
        if (request.getCode() != null && !request.getCode().matches("^[A-Za-z0-9_-]+$")) {
            errors.add("Promo code can only contain letters, numbers, underscores and hyphens");
        }

        // Validate discount type
        if (request.getDiscountType() == null) {
            errors.add("Discount type must not be null");
        }

        // Validate discount value
        if (request.getDiscountValue() == null) {
            errors.add("Discount value must not be null");
        }
        if (request.getDiscountValue() != null) {
            if (request.getDiscountType() == DiscountType.PERCENTAGE) {
                if (request.getDiscountValue().compareTo(java.math.BigDecimal.ZERO) <= 0 ||
                        request.getDiscountValue().compareTo(new java.math.BigDecimal("100")) > 0) {
                    errors.add("Percentage discount must be between 0 and 100");
                }
            } else if (request.getDiscountType() == DiscountType.FIXED) {
                if (request.getDiscountValue().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    errors.add("Fixed discount must be greater than 0");
                }
                if (request.getDiscountValue().compareTo(new java.math.BigDecimal("10000")) > 0) {
                    errors.add("Fixed discount cannot exceed 10,000");
                }
            }
        }

        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            errors.add("Currency must not be null or empty");
        }
        if (request.getCurrency() != null && !request.getCurrency().matches("^[A-Z]{3}$")) {
            errors.add("Currency must be a valid 3-letter ISO code");
        }

        // Validate minimum purchase amount
        if (request.getMinPurchaseAmount() != null && request.getMinPurchaseAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Minimum purchase amount must be greater than 0");
        }

        // Validate max discount amount
        if (request.getMaxDiscountAmount() != null && request.getMaxDiscountAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Maximum discount amount must be greater than 0");
        }

        // Validate usage limit
        if (request.getUsageLimit() != null && request.getUsageLimit() <= 0) {
            errors.add("Usage limit must be greater than 0");
        }

        // Validate dates
        if (request.getValidFrom() != null && request.getValidUntil() != null) {
            if (request.getValidUntil().isBefore(request.getValidFrom())) {
                errors.add("Valid until date must be after valid from date");
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid promo code request", ErrorCodes.PROMO_CODE_INVALID, errors);
        }
    }
}