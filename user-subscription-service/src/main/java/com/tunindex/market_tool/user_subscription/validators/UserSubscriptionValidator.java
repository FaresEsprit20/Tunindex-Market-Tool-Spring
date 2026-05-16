package com.tunindex.market_tool.user_subscription.validators;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.payment.entities.UserSubscription;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;
import com.tunindex.market_tool.user_subscription.dto.UserSubscriptionDto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserSubscriptionValidator {

    public static void validate(UserSubscriptionDto subscription) {
        List<String> errors = new ArrayList<>();

        if (subscription == null) {
            errors.add("User subscription cannot be null");
            throw new InvalidEntityException("User subscription is null", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }

        // Validate user ID
        if (subscription.getUserId() == null || subscription.getUserId() <= 0) {
            errors.add("User ID must be a positive number");
        }

        // Validate plan ID
        if (subscription.getPlanId() == null || subscription.getPlanId() <= 0) {
            errors.add("Plan ID must be a positive number");
        }

        // Validate dates
        if (subscription.getStartDate() == null) {
            errors.add("Start date must not be null");
        }

        if (subscription.getEndDate() == null) {
            errors.add("End date must not be null");
        }

        if (subscription.getStartDate() != null && subscription.getEndDate() != null &&
                subscription.getEndDate().isBefore(subscription.getStartDate())) {
            errors.add("End date must be after start date");
        }

        // Validate status
        if (subscription.getStatus() == null) {
            errors.add("Subscription status must not be null");
        }

        // Validate auto renew (default to true if null)
        if (subscription.getAutoRenew() == null) {
            subscription.setAutoRenew(true);
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid user subscription", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }
    }

    public static void validateCancellation(UserSubscription subscription, String reason) {
        List<String> errors = new ArrayList<>();

        if (subscription == null) {
            errors.add("Subscription not found");
            throw new InvalidEntityException("Subscription not found", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }

        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            errors.add("Subscription is already cancelled");
        }

        if (subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            errors.add("Cannot cancel an expired subscription");
        }

        if (reason != null && reason.length() > 500) {
            errors.add("Cancellation reason must not exceed 500 characters");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Cannot cancel subscription", ErrorCodes.SUBSCRIPTION_CANCELLED, errors);
        }
    }

    public static void validateRenewal(UserSubscription subscription) {
        List<String> errors = new ArrayList<>();

        if (subscription == null) {
            errors.add("Subscription not found");
            throw new InvalidEntityException("Subscription not found", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE && subscription.getStatus() != SubscriptionStatus.EXPIRED) {
            errors.add("Only active or expired subscriptions can be renewed");
        }

        if (subscription.getEndDate() != null && subscription.getEndDate().isAfter(LocalDateTime.now().plusDays(30))) {
            errors.add("Subscription can only be renewed within 30 days of expiration");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Cannot renew subscription", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }
    }

}