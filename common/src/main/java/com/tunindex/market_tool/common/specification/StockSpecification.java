package com.tunindex.market_tool.common.specification;

import com.tunindex.market_tool.common.entities.Stock;
import com.tunindex.market_tool.common.entities.enums.OwnershipType;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class StockSpecification {

    // ========== BASIC FILTERS ==========

    public static Specification<Stock> symbolContains(String symbol) {
        return (root, query, cb) -> {
            if (symbol == null || symbol.isEmpty()) return cb.conjunction();
            return cb.equal(cb.upper(root.get("symbol")), symbol.toUpperCase());
        };
    }

    public static Specification<Stock> symbolContainsPartial(String symbol) {
        return (root, query, cb) -> {
            if (symbol == null || symbol.isEmpty()) return cb.conjunction();
            return cb.like(cb.upper(root.get("symbol")), "%" + symbol.toUpperCase() + "%");
        };
    }

    public static Specification<Stock> nameContains(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isEmpty()) return cb.conjunction();
            return cb.like(cb.upper(root.get("name")), "%" + name.toUpperCase() + "%");
        };
    }

    public static Specification<Stock> exchangeEquals(String exchange) {
        return (root, query, cb) -> {
            if (exchange == null || exchange.isEmpty()) return cb.conjunction();
            return cb.equal(root.get("exchange"), exchange);
        };
    }

    public static Specification<Stock> sectorEquals(SectorType sector) {
        return (root, query, cb) -> {
            if (sector == null) return cb.conjunction();
            return cb.equal(root.get("sector"), sector);
        };
    }

    public static Specification<Stock> ownershipTypeEquals(OwnershipType ownershipType) {
        return (root, query, cb) -> {
            if (ownershipType == null) return cb.conjunction();
            return cb.equal(root.get("ownershipType"), ownershipType);
        };
    }

    // ========== PRICE FILTERS ==========

    public static Specification<Stock> priceBetween(BigDecimal minPrice, BigDecimal maxPrice) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("priceData")));
            predicates.add(cb.isNotNull(root.get("priceData").get("lastPrice")));
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceData").get("lastPrice"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceData").get("lastPrice"), maxPrice));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> priceGreaterThan(BigDecimal price) {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("priceData")),
                        cb.isNotNull(root.get("priceData").get("lastPrice")),
                        cb.greaterThan(root.get("priceData").get("lastPrice"), price)
                );
    }

    public static Specification<Stock> priceLessThan(BigDecimal price) {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("priceData")),
                        cb.isNotNull(root.get("priceData").get("lastPrice")),
                        cb.lessThan(root.get("priceData").get("lastPrice"), price)
                );
    }

    // ========== 52-WEEK FILTERS ==========

    public static Specification<Stock> closeTo52WeekLowPercentageBetween(BigDecimal minPct, BigDecimal maxPct) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("priceData")));
            predicates.add(cb.isNotNull(root.get("priceData").get("closeTo52weekslowPct")));
            if (minPct != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("priceData").get("closeTo52weekslowPct"), minPct));
            }
            if (maxPct != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("priceData").get("closeTo52weekslowPct"), maxPct));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> near52WeekLow(BigDecimal thresholdPercent) {
        // closeTo52weekslowPct = 100% means AT the low, 0% means AT the high.
        // "Near the low" means closeTo52weekslowPct >= threshold (default 90%).
        return (root, query, cb) -> {
            BigDecimal threshold = thresholdPercent != null ? thresholdPercent : new BigDecimal("90");
            return cb.and(
                    cb.isNotNull(root.get("priceData")),
                    cb.isNotNull(root.get("priceData").get("closeTo52weekslowPct")),
                    cb.greaterThanOrEqualTo(root.get("priceData").get("closeTo52weekslowPct"), threshold)
            );
        };
    }

    public static Specification<Stock> near52WeekHigh(BigDecimal thresholdPercent) {
        // closeTo52weekslowPct = 0% means AT the high, 100% means AT the low.
        // "Near the high" means closeTo52weekslowPct <= threshold (default 10%).
        return (root, query, cb) -> {
            BigDecimal threshold = thresholdPercent != null ? thresholdPercent : new BigDecimal("10");
            return cb.and(
                    cb.isNotNull(root.get("priceData")),
                    cb.isNotNull(root.get("priceData").get("closeTo52weekslowPct")),
                    cb.lessThanOrEqualTo(root.get("priceData").get("closeTo52weekslowPct"), threshold)
            );
        };
    }

    // ========== PROFIT MARGIN FILTERS ==========

    public static Specification<Stock> profitMarginBetween(BigDecimal minMargin, BigDecimal maxMargin) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("ratiosData")));
            predicates.add(cb.isNotNull(root.get("ratiosData").get("profitMargin")));
            if (minMargin != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("ratiosData").get("profitMargin"), minMargin));
            }
            if (maxMargin != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("ratiosData").get("profitMargin"), maxMargin));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> profitMarginGreaterThan(BigDecimal margin) {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("ratiosData")),
                        cb.isNotNull(root.get("ratiosData").get("profitMargin")),
                        cb.greaterThan(root.get("ratiosData").get("profitMargin"), margin)
                );
    }

    // ========== MARGIN OF SAFETY FILTERS ==========

    public static Specification<Stock> marginOfSafetyBetween(BigDecimal minMargin, BigDecimal maxMargin) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("calculatedValues")));
            predicates.add(cb.isNotNull(root.get("calculatedValues").get("marginOfSafety")));
            if (minMargin != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedValues").get("marginOfSafety"), minMargin));
            }
            if (maxMargin != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedValues").get("marginOfSafety"), maxMargin));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> undervalued() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("calculatedValues")),
                        cb.isNotNull(root.get("calculatedValues").get("marginOfSafety")),
                        cb.greaterThan(root.get("calculatedValues").get("marginOfSafety"), BigDecimal.ZERO)
                );
    }

    public static Specification<Stock> overvalued() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("calculatedValues")),
                        cb.isNotNull(root.get("calculatedValues").get("marginOfSafety")),
                        cb.lessThan(root.get("calculatedValues").get("marginOfSafety"), BigDecimal.ZERO)
                );
    }

    public static Specification<Stock> marginOfSafetyGreaterThan(BigDecimal margin) {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("calculatedValues")),
                        cb.isNotNull(root.get("calculatedValues").get("marginOfSafety")),
                        cb.greaterThan(root.get("calculatedValues").get("marginOfSafety"), margin)
                );
    }

    // ========== GRAHAM VALUE FILTERS ==========

    public static Specification<Stock> grahamFairValueBetween(BigDecimal minValue, BigDecimal maxValue) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("calculatedValues")));
            predicates.add(cb.isNotNull(root.get("calculatedValues").get("grahamFairValue")));
            if (minValue != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedValues").get("grahamFairValue"), minValue));
            }
            if (maxValue != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedValues").get("grahamFairValue"), maxValue));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> priceBelowGrahamValue() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("calculatedValues")),
                        cb.isNotNull(root.get("calculatedValues").get("grahamFairValue")),
                        cb.isNotNull(root.get("priceData")),
                        cb.isNotNull(root.get("priceData").get("lastPrice")),
                        cb.lessThan(root.get("priceData").get("lastPrice"),
                                root.get("calculatedValues").get("grahamFairValue"))
                );
    }

    public static Specification<Stock> priceAboveGrahamValue() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("calculatedValues")),
                        cb.isNotNull(root.get("calculatedValues").get("grahamFairValue")),
                        cb.isNotNull(root.get("priceData")),
                        cb.isNotNull(root.get("priceData").get("lastPrice")),
                        cb.greaterThan(root.get("priceData").get("lastPrice"),
                                root.get("calculatedValues").get("grahamFairValue"))
                );
    }

    // ========== DEBT/EQUITY FILTERS ==========

    public static Specification<Stock> debtToEquityBetween(BigDecimal minRatio, BigDecimal maxRatio) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("ratiosData")));
            predicates.add(cb.isNotNull(root.get("ratiosData").get("debtToEquity")));
            if (minRatio != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("ratiosData").get("debtToEquity"), minRatio));
            }
            if (maxRatio != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("ratiosData").get("debtToEquity"), maxRatio));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> lowDebt() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("ratiosData")),
                        cb.isNotNull(root.get("ratiosData").get("debtToEquity")),
                        cb.lessThan(root.get("ratiosData").get("debtToEquity"), new BigDecimal("0.5"))
                );
    }

    public static Specification<Stock> highDebt() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("ratiosData")),
                        cb.isNotNull(root.get("ratiosData").get("debtToEquity")),
                        cb.greaterThan(root.get("ratiosData").get("debtToEquity"), new BigDecimal("1.0"))
                );
    }

    // ========== EPS/BVPS FILTERS ==========

    public static Specification<Stock> epsBetween(BigDecimal minEps, BigDecimal maxEps) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("eps")));
            if (minEps != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fundamentalData").get("eps"), minEps));
            }
            if (maxEps != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fundamentalData").get("eps"), maxEps));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> profitable() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("fundamentalData")),
                        cb.isNotNull(root.get("fundamentalData").get("eps")),
                        cb.greaterThan(root.get("fundamentalData").get("eps"), BigDecimal.ZERO)
                );
    }

    public static Specification<Stock> bvpsBetween(BigDecimal minBvps, BigDecimal maxBvps) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("calculatedValues")));
            predicates.add(cb.isNotNull(root.get("calculatedValues").get("bookValuePerShare")));
            if (minBvps != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("calculatedValues").get("bookValuePerShare"), minBvps));
            }
            if (maxBvps != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("calculatedValues").get("bookValuePerShare"), maxBvps));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ========== PE RATIO FILTERS ==========

    public static Specification<Stock> peRatioBetween(BigDecimal minPe, BigDecimal maxPe) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("peRatio")));
            if (minPe != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fundamentalData").get("peRatio"), minPe));
            }
            if (maxPe != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fundamentalData").get("peRatio"), maxPe));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> lowPeRatio() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("fundamentalData")),
                        cb.isNotNull(root.get("fundamentalData").get("peRatio")),
                        cb.lessThan(root.get("fundamentalData").get("peRatio"), new BigDecimal("15"))
                );
    }

    // ========== DIVIDEND YIELD FILTERS ==========

    public static Specification<Stock> dividendYieldBetween(BigDecimal minYield, BigDecimal maxYield) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("dividendYield")));
            if (minYield != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fundamentalData").get("dividendYield"), minYield));
            }
            if (maxYield != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fundamentalData").get("dividendYield"), maxYield));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> highDividend() {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("fundamentalData")),
                        cb.isNotNull(root.get("fundamentalData").get("dividendYield")),
                        cb.greaterThan(root.get("fundamentalData").get("dividendYield"), new BigDecimal("4"))
                );
    }

    // ========== MARKET CAP FILTERS ==========

    public static Specification<Stock> marketCapBetween(BigDecimal minCap, BigDecimal maxCap) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("marketCap")));
            if (minCap != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fundamentalData").get("marketCap"), minCap));
            }
            if (maxCap != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fundamentalData").get("marketCap"), maxCap));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ========== VOLUME FILTERS ==========

    public static Specification<Stock> volumeGreaterThan(Long minVolume) {
        return (root, query, cb) ->
                cb.and(
                        cb.isNotNull(root.get("volumeData")),
                        cb.isNotNull(root.get("volumeData").get("volume")),
                        cb.greaterThan(root.get("volumeData").get("volume"), minVolume)
                );
    }

    // ========== DATE FILTERS ==========

    public static Specification<Stock> updatedAfter(LocalDateTime dateTime) {
        return (root, query, cb) -> {
            if (dateTime == null) return cb.conjunction();
            return cb.greaterThan(root.get("updatedAt"), dateTime);
        };
    }

    public static Specification<Stock> updatedBefore(LocalDateTime dateTime) {
        return (root, query, cb) -> {
            if (dateTime == null) return cb.conjunction();
            return cb.lessThan(root.get("updatedAt"), dateTime);
        };
    }

    // ========== COMBINED FILTERS (MUTUALLY EXCLUSIVE - USE ONLY ONE) ==========

    public static Specification<Stock> valueInvestorFavorites() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // marginOfSafety > 20%
            predicates.add(cb.isNotNull(root.get("calculatedValues")));
            predicates.add(cb.isNotNull(root.get("calculatedValues").get("marginOfSafety")));
            predicates.add(cb.greaterThan(root.get("calculatedValues").get("marginOfSafety"), new BigDecimal("20")));

            // profitable (EPS > 0)
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("eps")));
            predicates.add(cb.greaterThan(root.get("fundamentalData").get("eps"), BigDecimal.ZERO));

            // low debt (D/E < 0.5)
            predicates.add(cb.isNotNull(root.get("ratiosData")));
            predicates.add(cb.isNotNull(root.get("ratiosData").get("debtToEquity")));
            predicates.add(cb.lessThan(root.get("ratiosData").get("debtToEquity"), new BigDecimal("0.5")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> growthInvestorFavorites() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // profit margin > 15%
            predicates.add(cb.isNotNull(root.get("ratiosData")));
            predicates.add(cb.isNotNull(root.get("ratiosData").get("profitMargin")));
            predicates.add(cb.greaterThan(root.get("ratiosData").get("profitMargin"), new BigDecimal("15")));

            // PE ratio between 0 and 25
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("peRatio")));
            predicates.add(cb.greaterThanOrEqualTo(root.get("fundamentalData").get("peRatio"), BigDecimal.ZERO));
            predicates.add(cb.lessThanOrEqualTo(root.get("fundamentalData").get("peRatio"), new BigDecimal("25")));

            // positive margin of safety
            predicates.add(cb.isNotNull(root.get("calculatedValues")));
            predicates.add(cb.isNotNull(root.get("calculatedValues").get("marginOfSafety")));
            predicates.add(cb.greaterThan(root.get("calculatedValues").get("marginOfSafety"), BigDecimal.ZERO));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> incomeInvestorFavorites() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // high dividend (> 4%) AND profitable — guard fundamentalData once for both fields
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("dividendYield")));
            predicates.add(cb.greaterThan(root.get("fundamentalData").get("dividendYield"), new BigDecimal("4")));
            // FIX: added missing isNotNull guard for eps (was piggy-backing on the dividendYield parent check)
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("eps")));
            predicates.add(cb.greaterThan(root.get("fundamentalData").get("eps"), BigDecimal.ZERO));

            // low debt (D/E < 0.5)
            predicates.add(cb.isNotNull(root.get("ratiosData")));
            predicates.add(cb.isNotNull(root.get("ratiosData").get("debtToEquity")));
            predicates.add(cb.lessThan(root.get("ratiosData").get("debtToEquity"), new BigDecimal("0.5")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> contrarianFavorites() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // near 52-week low: closeTo52weekslowPct >= 90 means price is within 10% of the low
            // (100% = AT the low, 0% = AT the high — so high values = close to low)
            predicates.add(cb.isNotNull(root.get("priceData")));
            predicates.add(cb.isNotNull(root.get("priceData").get("closeTo52weekslowPct")));
            predicates.add(cb.greaterThanOrEqualTo(root.get("priceData").get("closeTo52weekslowPct"), new BigDecimal("90")));

            // profitable (EPS > 0)
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("eps")));
            predicates.add(cb.greaterThan(root.get("fundamentalData").get("eps"), BigDecimal.ZERO));

            // FIX: added missing isNotNull guard for marketCap (was missing the parent null check re-assertion)
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("marketCap")));
            predicates.add(cb.greaterThan(root.get("fundamentalData").get("marketCap"), new BigDecimal("1000000000")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> grahamCriteria() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // price below graham fair value
            predicates.add(cb.isNotNull(root.get("calculatedValues")));
            predicates.add(cb.isNotNull(root.get("calculatedValues").get("grahamFairValue")));
            predicates.add(cb.isNotNull(root.get("priceData")));
            predicates.add(cb.isNotNull(root.get("priceData").get("lastPrice")));
            predicates.add(cb.lessThan(root.get("priceData").get("lastPrice"),
                    root.get("calculatedValues").get("grahamFairValue")));

            // low PE ratio (< 15)
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("peRatio")));
            predicates.add(cb.lessThan(root.get("fundamentalData").get("peRatio"), new BigDecimal("15")));

            // low debt (D/E < 0.5)
            predicates.add(cb.isNotNull(root.get("ratiosData")));
            predicates.add(cb.isNotNull(root.get("ratiosData").get("debtToEquity")));
            predicates.add(cb.lessThan(root.get("ratiosData").get("debtToEquity"), new BigDecimal("0.5")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Stock> grahamCriteriaRelaxed() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // FIX: replaced cb.prod(..., 1.2) double literal (type mismatch with BigDecimal lastPrice)
            // with a properly typed BigDecimal literal expression to avoid ClassCastException at runtime
            predicates.add(cb.isNotNull(root.get("calculatedValues")));
            predicates.add(cb.isNotNull(root.get("calculatedValues").get("grahamFairValue")));
            predicates.add(cb.isNotNull(root.get("priceData")));
            predicates.add(cb.isNotNull(root.get("priceData").get("lastPrice")));
            predicates.add(cb.lessThan(
                    root.<BigDecimal>get("priceData").get("lastPrice"),
                    cb.prod(root.<BigDecimal>get("calculatedValues").get("grahamFairValue"),
                            cb.literal(new BigDecimal("1.2")))
            ));

            // PE ratio < 20 (relaxed)
            predicates.add(cb.isNotNull(root.get("fundamentalData")));
            predicates.add(cb.isNotNull(root.get("fundamentalData").get("peRatio")));
            predicates.add(cb.lessThan(root.get("fundamentalData").get("peRatio"), new BigDecimal("20")));

            // debt < 1.0 (relaxed)
            predicates.add(cb.isNotNull(root.get("ratiosData")));
            predicates.add(cb.isNotNull(root.get("ratiosData").get("debtToEquity")));
            predicates.add(cb.lessThan(root.get("ratiosData").get("debtToEquity"), new BigDecimal("1.0")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ========== UTILITY METHODS ==========

    public static Specification<Stock> empty() {
        return (root, query, cb) -> cb.conjunction();
    }

    @SafeVarargs
    public static Specification<Stock> allOf(Specification<Stock>... specs) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Specification<Stock> spec : specs) {
                if (spec != null) {
                    Predicate predicate = spec.toPredicate(root, query, cb);
                    if (predicate != null) {
                        predicates.add(predicate);
                    }
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    @SafeVarargs
    public static Specification<Stock> anyOf(Specification<Stock>... specs) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Specification<Stock> spec : specs) {
                if (spec != null) {
                    Predicate predicate = spec.toPredicate(root, query, cb);
                    if (predicate != null) {
                        predicates.add(predicate);
                    }
                }
            }
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
