package com.tunindex.market_tool.user_subscription.specifications;

import com.tunindex.market_tool.payment.entities.SubscriptionPlan;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

public class SubscriptionPlanSpecification {

    public static Specification<SubscriptionPlan> nameContains(String name) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(name)) return null;
            return cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
        };
    }

    public static Specification<SubscriptionPlan> isActive(Boolean isActive) {
        return (root, query, cb) -> {
            if (isActive == null) return null;
            return cb.equal(root.get("isActive"), isActive);
        };
    }

    public static Specification<SubscriptionPlan> priceMonthlyBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return null;
            if (min != null && max != null) {
                return cb.between(root.get("priceMonthly"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("priceMonthly"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("priceMonthly"), max);
            }
        };
    }

    public static Specification<SubscriptionPlan> priceYearlyBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return null;
            if (min != null && max != null) {
                return cb.between(root.get("priceYearly"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("priceYearly"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("priceYearly"), max);
            }
        };
    }

    public static Specification<SubscriptionPlan> apiCallsLimitGreaterThan(Integer limit) {
        return (root, query, cb) -> {
            if (limit == null) return null;
            return cb.greaterThanOrEqualTo(root.get("apiCallsLimit"), limit);
        };
    }

    public static Specification<SubscriptionPlan> empty() {
        return (root, query, cb) -> cb.conjunction();
    }
}