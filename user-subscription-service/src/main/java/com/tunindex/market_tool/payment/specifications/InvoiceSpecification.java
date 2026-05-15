package com.tunindex.market_tool.payment.specifications;

import com.tunindex.market_tool.payment.entities.Invoice;
import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InvoiceSpecification {

    public static Specification<Invoice> userIdEquals(Long userId) {
        return (root, query, cb) -> {
            if (userId == null) return null;
            return cb.equal(root.get("userId"), userId);
        };
    }

    public static Specification<Invoice> invoiceNumberContains(String invoiceNumber) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(invoiceNumber)) return null;
            return cb.like(cb.lower(root.get("invoiceNumber")), "%" + invoiceNumber.toLowerCase() + "%");
        };
    }

    public static Specification<Invoice> transactionIdEquals(Long transactionId) {
        return (root, query, cb) -> {
            if (transactionId == null) return null;
            return cb.equal(root.get("transactionId"), transactionId);
        };
    }

    public static Specification<Invoice> amountBetween(BigDecimal min, BigDecimal max) {
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

    public static Specification<Invoice> totalAmountBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) return null;
            if (min != null && max != null) {
                return cb.between(root.get("totalAmount"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("totalAmount"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("totalAmount"), max);
            }
        };
    }

    public static Specification<Invoice> currencyEquals(String currency) {
        return (root, query, cb) -> {
            if (!StringUtils.hasLength(currency)) return null;
            return cb.equal(cb.upper(root.get("currency")), currency.toUpperCase());
        };
    }

    public static Specification<Invoice> statusEquals(InvoiceStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Invoice> statusIn(InvoiceStatus... statuses) {
        return (root, query, cb) -> {
            if (statuses == null || statuses.length == 0) return null;
            return root.get("status").in((Object[]) statuses);
        };
    }

    public static Specification<Invoice> issueDateBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) {
                return cb.between(root.get("issueDate"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("issueDate"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("issueDate"), end);
            }
        };
    }

    public static Specification<Invoice> dueDateBetween(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) {
                return cb.between(root.get("dueDate"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("dueDate"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("dueDate"), end);
            }
        };
    }

    public static Specification<Invoice> isOverdue(LocalDateTime now) {
        // Make a final copy of the parameter to use in lambda
        final LocalDateTime currentTime = (now != null) ? now : LocalDateTime.now();

        return (root, query, cb) -> cb.and(
                cb.lessThan(root.get("dueDate"), currentTime),
                cb.notEqual(root.get("status"), InvoiceStatus.PAID),
                cb.notEqual(root.get("status"), InvoiceStatus.CANCELLED)
        );
    }

    public static Specification<Invoice> empty() {
        return (root, query, cb) -> cb.conjunction();
    }
}