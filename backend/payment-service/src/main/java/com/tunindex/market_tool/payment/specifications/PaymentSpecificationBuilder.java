package com.tunindex.market_tool.payment.specifications;

import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PaymentSpecificationBuilder<T> {

    private final List<Specification<T>> specifications = new ArrayList<>();

    public PaymentSpecificationBuilder<T> with(Specification<T> spec) {
        if (spec != null) {
            specifications.add(spec);
        }
        return this;
    }

    public PaymentSpecificationBuilder<T> withIfPresent(Specification<T> spec, boolean condition) {
        if (condition && spec != null) {
            specifications.add(spec);
        }
        return this;
    }

    public Specification<T> build() {
        if (specifications.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }

        // Use and() method directly on Specification (no deprecated where())
        Specification<T> result = specifications.get(0);
        for (int i = 1; i < specifications.size(); i++) {
            result = result.and(specifications.get(i));
        }
        return result;
    }

    public static <T> PaymentSpecificationBuilder<T> builder() {
        return new PaymentSpecificationBuilder<>();
    }

    public void clear() {
        specifications.clear();
    }

}