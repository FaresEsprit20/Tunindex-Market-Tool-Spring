package com.tunindex.market_tool.user_subscription.validators;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.entities.SubscriptionPlan;
import com.tunindex.market_tool.user_subscription.dto.SubscriptionPlanDto;

import java.util.ArrayList;
import java.util.List;

public class SubscriptionPlanValidator {

    public static void validate(SubscriptionPlanDto plan) {
        List<String> errors = new ArrayList<>();

        if (plan == null) {
            errors.add("Subscription plan cannot be null");
            throw new InvalidEntityException("Subscription plan is null", ErrorCodes.PLAN_NOT_FOUND, errors);
        }

        // Validate name
        if (plan.getName() == null || plan.getName().trim().isEmpty()) {
            errors.add("Plan name must not be null or empty");
        }
        if (plan.getName() != null && (plan.getName().length() < 2 || plan.getName().length() > 50)) {
            errors.add("Plan name must be between 2 and 50 characters");
        }

        // Validate price monthly
        if (plan.getPriceMonthly() == null) {
            errors.add("Monthly price must not be null");
        }
        if (plan.getPriceMonthly() != null && plan.getPriceMonthly().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Monthly price must be greater than 0");
        }

        // Validate price yearly (optional)
        if (plan.getPriceYearly() != null && plan.getPriceYearly().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            errors.add("Yearly price must be greater than 0 if provided");
        }

        // Validate currency
        if (plan.getCurrency() == null || plan.getCurrency().trim().isEmpty()) {
            errors.add("Currency must not be null or empty");
        }
        if (plan.getCurrency() != null && !plan.getCurrency().matches("^[A-Z]{3}$")) {
            errors.add("Currency must be a valid 3-letter ISO code (e.g., TND, USD, EUR)");
        }

        // Validate API calls limit
        if (plan.getApiCallsLimit() != null && plan.getApiCallsLimit() < 0) {
            errors.add("API calls limit cannot be negative");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid subscription plan", ErrorCodes.PLAN_NOT_FOUND, errors);
        }
    }

    public static void validateForUpdate(SubscriptionPlan existingPlan, SubscriptionPlanDto updatePlan) {
        List<String> errors = new ArrayList<>();

        if (existingPlan == null) {
            errors.add("Subscription plan to update not found");
            throw new InvalidEntityException("Subscription plan not found", ErrorCodes.PLAN_NOT_FOUND, errors);
        }

        if (updatePlan == null) {
            errors.add("Update data cannot be null");
            throw new InvalidEntityException("Update data is null", ErrorCodes.PLAN_NOT_FOUND, errors);
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid update data", ErrorCodes.PLAN_NOT_FOUND, errors);
        }
    }
}