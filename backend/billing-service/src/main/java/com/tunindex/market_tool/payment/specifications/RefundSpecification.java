package com.tunindex.market_tool.payment.specifications;

import com.tunindex.market_tool.payment.entities.Refund;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RefundSpecification {

    public static Specification<Refund> transactionIdEquals(Long transactionId) {
        return (root, query, cb) -> {
            if (transactionId == null) return null;
            return cb.equal(root.get("transactionId"), transactionId);
        };
    }

    public static Specification<Refund> amountBetween(BigDecimal min, BigDecimal max) {
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

    public static Specification<Refund> reasonContains(String reason) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(reason)) return null;
            return cb.like(cb.lower(root.get("reason")), "%" + reason.toLowerCase() + "%");
        };
    }

    public static Specification<Refund> statusEquals(RefundStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Refund> statusIn(RefundStatus... statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.length == 0) return null;
            return root.get("status").in((Object[]) statuses);
        };
    }

    public static Specification<Refund> providerRefundIdEquals(String providerRefundId) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(providerRefundId)) return null;
            return cb.equal(root.get("providerRefundId"), providerRefundId);
        };
    }

    public static Specification<Refund> refundDateBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) {
                return cb.between(root.get("refundDate"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("refundDate"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("refundDate"), end);
            }
        };
    }

    public static Specification<Refund> createdAtBetween(LocalDateTime start, LocalDateTime end) {
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

    public static Specification<Refund> isCompleted() {
        return (root, query, cb) -> cb.equal(root.get("status"), RefundStatus.COMPLETED);
    }

    public static Specification<Refund> isPending() {
        return (root, query, cb) -> cb.equal(root.get("status"), RefundStatus.PENDING);
    }

    public static Specification<Refund> empty() {
        return (root, query, cb) -> cb.conjunction();
    }
}