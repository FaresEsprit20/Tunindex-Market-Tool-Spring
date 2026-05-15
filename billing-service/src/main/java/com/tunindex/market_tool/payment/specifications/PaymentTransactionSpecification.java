package com.tunindex.market_tool.payment.specifications;

import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.enums.PaymentMethod;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentTransactionSpecification {

    public static Specification<PaymentTransaction> userIdEquals(Long userId) {
        return (root, query, cb) -> {
            if (userId == null) return null;
            return cb.equal(root.get("userId"), userId);
        };
    }

    public static Specification<PaymentTransaction> transactionIdContains(String transactionId) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(transactionId)) return null;
            return cb.like(cb.lower(root.get("transactionId")), "%" + transactionId.toLowerCase() + "%");
        };
    }

    public static Specification<PaymentTransaction> providerPaymentIdEquals(String providerPaymentId) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(providerPaymentId)) return null;
            return cb.equal(root.get("providerPaymentId"), providerPaymentId);
        };
    }

    public static Specification<PaymentTransaction> amountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return null;
            if (min != null && max != null) {
                return cb.between(root.get("amount"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("amount"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("amount"), max);
            }
        };
    }

    public static Specification<PaymentTransaction> currencyEquals(String currency) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(currency)) return null;
            return cb.equal(cb.upper(root.get("currency")), currency.toUpperCase());
        };
    }

    public static Specification<PaymentTransaction> paymentMethodEquals(PaymentMethod paymentMethod) {
        return (root, query, cb) -> {
            if (paymentMethod == null) return null;
            return cb.equal(root.get("paymentMethod"), paymentMethod);
        };
    }

    public static Specification<PaymentTransaction> paymentMethodIn(PaymentMethod... methods) {
        return (root, query, cb) -> {
            if (methods == null || methods.length == 0) return null;
            return root.get("paymentMethod").in((Object[]) methods);
        };
    }

    public static Specification<PaymentTransaction> statusEquals(PaymentStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<PaymentTransaction> statusIn(PaymentStatus... statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.length == 0) return null;
            return root.get("status").in((Object[]) statuses);
        };
    }

    public static Specification<PaymentTransaction> providerNameEquals(String providerName) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(providerName)) return null;
            return cb.equal(cb.lower(root.get("providerName")), providerName.toLowerCase());
        };
    }

    public static Specification<PaymentTransaction> subscriptionIdEquals(Long subscriptionId) {
        return (root, query, cb) -> {
            if (subscriptionId == null) return null;
            return cb.equal(root.get("subscriptionId"), subscriptionId);
        };
    }

    public static Specification<PaymentTransaction> paymentDateBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) {
                return cb.between(root.get("paymentDate"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("paymentDate"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("paymentDate"), end);
            }
        };
    }

    public static Specification<PaymentTransaction> createdAtBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) {
                return cb.between(root.get("createdAt"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("createdAt"), end);
            }
        };
    }

    public static Specification<PaymentTransaction> empty() {
        return (root, query, cb) -> cb.conjunction();
    }
}