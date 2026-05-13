package com.tunindex.market_tool.payment.specifications;

import com.tunindex.market_tool.payment.entities.UserSubscription;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

public class UserSubscriptionSpecification {

    public static Specification<UserSubscription> userIdEquals(Long userId) {
        return (root, query, cb) -> {
            if (userId == null) return null;
            return cb.equal(root.get("userId"), userId);
        };
    }

    public static Specification<UserSubscription> planIdEquals(Long planId) {
        return (root, query, cb) -> {
            if (planId == null) return null;
            return cb.equal(root.get("plan").get("id"), planId);
        };
    }

    public static Specification<UserSubscription> statusEquals(SubscriptionStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<UserSubscription> statusIn(SubscriptionStatus... statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.length == 0) return null;
            return root.get("status").in((Object[]) statuses);
        };
    }

    public static Specification<UserSubscription> billingPeriodEquals(String billingPeriod) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(billingPeriod)) return null;
            return cb.equal(cb.lower(root.get("billingPeriod")), billingPeriod.toLowerCase());
        };
    }

    public static Specification<UserSubscription> autoRenewEquals(Boolean autoRenew) {
        return (root, query, cb) -> {
            if (autoRenew == null) return null;
            return cb.equal(root.get("autoRenew"), autoRenew);
        };
    }

    public static Specification<UserSubscription> startDateBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) {
                return cb.between(root.get("startDate"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("startDate"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("startDate"), end);
            }
        };
    }

    public static Specification<UserSubscription> endDateBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) {
                return cb.between(root.get("endDate"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("endDate"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("endDate"), end);
            }
        };
    }

    public static Specification<UserSubscription> isActive(LocalDateTime now) {
        // Create final copy to use in lambda
        final LocalDateTime currentTime = (now != null) ? now : LocalDateTime.now();

        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), SubscriptionStatus.ACTIVE),
                cb.lessThanOrEqualTo(root.get("startDate"), currentTime),
                cb.greaterThanOrEqualTo(root.get("endDate"), currentTime)
        );
    }

    public static Specification<UserSubscription> isExpired(LocalDateTime now) {
        // Create final copy to use in lambda
        final LocalDateTime currentTime = (now != null) ? now : LocalDateTime.now();

        return (root, query, cb) -> cb.lessThan(root.get("endDate"), currentTime);
    }

    public static Specification<UserSubscription> empty() {
        return (root, query, cb) -> cb.conjunction();
    }
}