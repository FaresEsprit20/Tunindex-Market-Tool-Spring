package com.tunindex.market_tool.collector.services.scoring;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.dto.news.NewsImpactDto;
import com.tunindex.market_tool.collector.dto.scoring.OpportunityScoreDto;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.common.entities.enums.OwnershipType;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The Tunindex Scorer: a transparent, rule-based buy-opportunity rating.
 *
 * <p>Six components, each scored 0-100 from real stored data, then blended
 * with fixed weights. There is no model and no prediction here — every
 * point awarded traces to a published figure (a filing-derived ratio, a
 * scraped close, a real headline), and the reasons list quotes the figure
 * that earned it so the user can check the arithmetic.
 *
 * <p>Weights lean toward valuation and timing because the question this
 * answers is "is this a good thing to buy <em>right now</em>" — quality
 * alone doesn't make an entry point, and a cheap price on a failing
 * business isn't one either.
 *
 * <p>A component whose inputs are entirely missing scores null rather than
 * zero, and is dropped from the blend (the remaining weights are
 * renormalised) so a stock isn't punished for data this app never had.
 * {@code dataCompleteness} reports how much was actually available.
 */
@Component
public class TunindexScorer {

    private static final int WEIGHT_VALUATION = 30;
    private static final int WEIGHT_TIMING = 25;
    private static final int WEIGHT_FINANCIAL_HEALTH = 20;
    private static final int WEIGHT_INCOME = 10;
    private static final int WEIGHT_MOMENTUM = 10;
    private static final int WEIGHT_NEWS = 5;

    /** Headlines older than this stop counting toward the news component. */
    private static final int NEWS_LOOKBACK_DAYS = 90;

    /**
     * closeTo52weekslowPct at or above this counts as "near the low" — the
     * field runs 100 = at the low, 0 = at the high, so 85 means the price
     * sits in the bottom 15% of its year. Matches the near52WeekLow screener
     * filter's intent, one notch looser than its 90 default so the upgrade
     * catches a stock on approach rather than only at the exact bottom.
     */
    private static final BigDecimal NEAR_LOW_THRESHOLD = new BigDecimal("85");

    public OpportunityScoreDto score(Stock stock, TechnicalAnalysisDto technical, List<NewsImpactDto> news) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Integer valuation = scoreValuation(stock, reasons, warnings);
        Integer timing = scoreTiming(stock, technical, reasons, warnings);
        Integer health = scoreFinancialHealth(stock, reasons, warnings);
        Integer income = scoreIncome(stock, reasons);
        Integer momentum = scoreMomentum(stock, technical, reasons, warnings);
        Integer newsScore = scoreNews(news, reasons, warnings);

        int overall = blend(
                new Component(valuation, WEIGHT_VALUATION),
                new Component(timing, WEIGHT_TIMING),
                new Component(health, WEIGHT_FINANCIAL_HEALTH),
                new Component(income, WEIGHT_INCOME),
                new Component(momentum, WEIGHT_MOMENTUM),
                new Component(newsScore, WEIGHT_NEWS));

        int present = count(valuation, timing, health, income, momentum, newsScore);
        int completeness = (int) Math.round(present * 100.0 / 6);

        return OpportunityScoreDto.builder()
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .sector(stock.getSector() != null ? stock.getSector().name() : null)
                .lastPrice(stock.getPriceData() != null ? stock.getPriceData().getLastPrice() : null)
                .currency(stock.getCurrency())
                .overallScore(overall)
                .verdict(verdict(overall, completeness, stock, isNearFiftyTwoWeekLow(stock), reasons, warnings))
                .valuationScore(valuation)
                .financialHealthScore(health)
                .timingScore(timing)
                .incomeScore(income)
                .momentumScore(momentum)
                .newsScore(newsScore)
                .dataCompleteness(completeness)
                .reasons(reasons)
                .warnings(warnings)
                .build();
    }

    // ── Valuation: how much cheaper than its own worth is it? ──────────────

    private Integer scoreValuation(Stock stock, List<String> reasons, List<String> warnings) {
        BigDecimal marginOfSafety = stock.getCalculatedValues() != null
                ? stock.getCalculatedValues().getMarginOfSafety() : null;
        BigDecimal peRatio = stock.getFundamentalData() != null
                ? stock.getFundamentalData().getPeRatio() : null;
        BigDecimal priceToBook = stock.getRatiosData() != null
                ? stock.getRatiosData().getPriceToBook() : null;

        if (marginOfSafety == null && peRatio == null && priceToBook == null) {
            return null;
        }

        List<Integer> parts = new ArrayList<>();

        if (marginOfSafety != null) {
            // 0% margin -> 50, +50% or better -> 100, -50% or worse -> 0.
            int mosScore = clamp(50 + marginOfSafety.doubleValue());
            parts.add(mosScore);
            if (marginOfSafety.compareTo(new BigDecimal("20")) > 0) {
                reasons.add(String.format("Trades %.1f%% below its Graham fair value", marginOfSafety.doubleValue()));
            } else if (marginOfSafety.compareTo(BigDecimal.ZERO) < 0) {
                warnings.add(String.format("Priced %.1f%% above Graham fair value", Math.abs(marginOfSafety.doubleValue())));
            }
        }

        if (peRatio != null) {
            double pe = peRatio.doubleValue();
            // Under 10 is cheap (100), 25+ is rich (0), negative earnings is a warning.
            int peScore = pe <= 0 ? 0 : clamp((25 - pe) / 15 * 100);
            parts.add(peScore);
            if (pe > 0 && pe < 12) {
                reasons.add(String.format("P/E of %.1f", pe));
            } else if (pe > 30) {
                warnings.add(String.format("P/E of %.1f is expensive", pe));
            }
        }

        if (priceToBook != null) {
            double pb = priceToBook.doubleValue();
            // Below 1x book is the classic value marker; above 4x is rich.
            int pbScore = pb <= 0 ? 50 : clamp((4 - pb) / 3 * 100);
            parts.add(pbScore);
            if (pb > 0 && pb < 1) {
                reasons.add(String.format("Trades at %.2fx book value", pb));
            }
        }

        return average(parts);
    }

    // ── Timing: is now a good moment to enter? ─────────────────────────────

    private Integer scoreTiming(Stock stock, TechnicalAnalysisDto technical, List<String> reasons, List<String> warnings) {
        BigDecimal closeTo52WeekLow = stock.getPriceData() != null
                ? stock.getPriceData().getCloseTo52weekslowPct() : null;
        return scoreTimingFrom(closeTo52WeekLow, technical, reasons, warnings);
    }

    /**
     * The timing component, taking its inputs directly rather than reading
     * them off a Stock.
     *
     * <p>Public because the backtester calls it with values reconstructed as
     * of a past date. That is deliberate: a backtest that re-implements the
     * scoring it claims to be testing measures its own copy, not the thing
     * that ships. This way both paths run the same arithmetic.
     */
    public Integer scoreTimingFrom(BigDecimal closeTo52WeekLow, TechnicalAnalysisDto technical,
                                   List<String> reasons, List<String> warnings) {
        List<Integer> parts = new ArrayList<>();

        if (closeTo52WeekLow != null) {
            // 100 = at the 52-week low (best entry), 0 = at the high.
            double pct = closeTo52WeekLow.doubleValue();
            parts.add(clamp(pct));
            if (pct >= 85) {
                reasons.add(String.format("Within %.0f%% of its 52-week low", 100 - pct));
            } else if (pct <= 10) {
                warnings.add("Trading at the top of its 52-week range");
            }
        }

        if (technical != null) {
            if (technical.getRsi14() != null) {
                double rsi = technical.getRsi14().doubleValue();
                // RSI 30 (oversold) is the best entry, 70 (overbought) the worst.
                int rsiScore = clamp((70 - rsi) / 40 * 100);
                parts.add(rsiScore);
                if (rsi < 35) {
                    reasons.add(String.format("RSI %.1f — oversold", rsi));
                } else if (rsi > 70) {
                    warnings.add(String.format("RSI %.1f — overbought", rsi));
                }
            }

            if (technical.getStochasticK() != null) {
                double k = technical.getStochasticK().doubleValue();
                parts.add(clamp(100 - k));
                if (k < 20) {
                    reasons.add(String.format("Stochastic %%K %.1f — oversold", k));
                }
            }

            if ("BULLISH_CROSS".equals(technical.getMacdCrossSignal())) {
                parts.add(85);
                reasons.add("MACD just crossed bullish");
            } else if ("BEARISH_CROSS".equals(technical.getMacdCrossSignal())) {
                parts.add(25);
                warnings.add("MACD just crossed bearish");
            }
        }

        return average(parts);
    }

    // ── Financial health: can the business carry itself? ───────────────────

    private Integer scoreFinancialHealth(Stock stock, List<String> reasons, List<String> warnings) {
        BigDecimal debtToEquity = stock.getRatiosData() != null
                ? stock.getRatiosData().getDebtToEquity() : null;
        BigDecimal profitMargin = stock.getRatiosData() != null
                ? stock.getRatiosData().getProfitMargin() : null;
        BigDecimal eps = stock.getFundamentalData() != null
                ? stock.getFundamentalData().getEps() : null;

        if (debtToEquity == null && profitMargin == null && eps == null) {
            return null;
        }

        List<Integer> parts = new ArrayList<>();

        if (debtToEquity != null) {
            double de = debtToEquity.doubleValue();
            // Debt-free is 100; 2.0x equity or worse is 0.
            parts.add(clamp((2.0 - de) / 2.0 * 100));
            if (de < 0.3) {
                reasons.add(String.format("Low leverage — debt/equity %.2f", de));
            } else if (de > 1.5) {
                warnings.add(String.format("High leverage — debt/equity %.2f", de));
            }
        }

        if (profitMargin != null) {
            double margin = profitMargin.doubleValue();
            // 25%+ margin is excellent, negative is a failing business.
            parts.add(clamp(margin / 25 * 100));
            if (margin > 20) {
                reasons.add(String.format("Profit margin %.1f%%", margin));
            } else if (margin < 0) {
                warnings.add(String.format("Loss-making — margin %.1f%%", margin));
            }
        }

        if (eps != null) {
            parts.add(eps.compareTo(BigDecimal.ZERO) > 0 ? 100 : 0);
            if (eps.compareTo(BigDecimal.ZERO) <= 0) {
                warnings.add("Negative earnings per share");
            }
        }

        return average(parts);
    }

    // ── Income: does holding it pay? ───────────────────────────────────────

    private Integer scoreIncome(Stock stock, List<String> reasons) {
        BigDecimal dividendYield = stock.getFundamentalData() != null
                ? stock.getFundamentalData().getDividendYield() : null;
        if (dividendYield == null) {
            return null;
        }
        double yield = dividendYield.doubleValue();
        // 8%+ is a top payer on this exchange; 0% scores 0.
        int score = clamp(yield / 8 * 100);
        if (yield >= 5) {
            reasons.add(String.format("Dividend yield %.1f%%", yield));
        }
        return score;
    }

    // ── Momentum: is the market already moving with it? ────────────────────

    private Integer scoreMomentum(Stock stock, TechnicalAnalysisDto technical, List<String> reasons, List<String> warnings) {
        List<Integer> parts = new ArrayList<>();

        BigDecimal oneYearReturn = stock.getFundamentalData() != null
                ? stock.getFundamentalData().getOneYearReturn() : null;
        if (oneYearReturn != null) {
            double ret = oneYearReturn.doubleValue();
            // -30% -> 0, +30% -> 100, flat -> 50.
            parts.add(clamp(50 + ret / 30 * 50));
            if (ret > 20) {
                reasons.add(String.format("Up %.1f%% over the past year", ret));
            } else if (ret < -20) {
                warnings.add(String.format("Down %.1f%% over the past year", Math.abs(ret)));
            }
        }

        if (technical != null && technical.getTrendSignal() != null) {
            switch (technical.getTrendSignal()) {
                case "BULLISH" -> {
                    parts.add(80);
                    reasons.add("Price above both its 20 and 50-day averages");
                }
                case "BEARISH" -> parts.add(30);
                default -> parts.add(50);
            }
        }

        return average(parts);
    }

    // ── News: what has actually been published about it lately? ────────────

    private Integer scoreNews(List<NewsImpactDto> news, List<String> reasons, List<String> warnings) {
        if (news == null || news.isEmpty()) {
            return null;
        }

        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(NEWS_LOOKBACK_DAYS);
        long positive = 0;
        long negative = 0;
        long considered = 0;

        for (NewsImpactDto item : news) {
            if (item.getPublishedAt() == null || item.getPublishedAt().isBefore(cutoff)) {
                continue;
            }
            considered++;
            if ("POSITIVE".equals(item.getSentiment())) {
                positive++;
            } else if ("NEGATIVE".equals(item.getSentiment())) {
                negative++;
            }
        }

        if (considered == 0) {
            return null;
        }

        // Neutral coverage sits at 50; net positive pushes up, net negative down.
        double net = (double) (positive - negative) / considered;
        int score = clamp(50 + net * 50);

        if (positive > 0 && positive > negative) {
            reasons.add(String.format("%d positive headline%s in the last %d days",
                    positive, positive == 1 ? "" : "s", NEWS_LOOKBACK_DAYS));
        }
        if (negative > positive) {
            warnings.add(String.format("%d negative headline%s in the last %d days",
                    negative, negative == 1 ? "" : "s", NEWS_LOOKBACK_DAYS));
        }

        return score;
    }

    // ── Blending ───────────────────────────────────────────────────────────

    private record Component(Integer score, int weight) {}

    /**
     * Weighted mean over the components that actually have data. Missing
     * components drop out and the remaining weights renormalise, so a stock
     * with no dividend data isn't scored as though it pays nothing.
     */
    private int blend(Component... components) {
        int weightedSum = 0;
        int totalWeight = 0;
        for (Component component : components) {
            if (component.score() != null) {
                weightedSum += component.score() * component.weight();
                totalWeight += component.weight();
            }
        }
        return totalWeight == 0 ? 0 : (int) Math.round((double) weightedSum / totalWeight);
    }

    /**
     * Turns the blended score into a call, then applies two overrides that
     * sit outside the arithmetic because they are policy, not weighting.
     *
     * @param nearFiftyTwoWeekLow price within {@link #NEAR_LOW_THRESHOLD}% of
     *                            the bottom of its 52-week range
     */
    private String verdict(int overall, int completeness, Stock stock,
                           boolean nearFiftyTwoWeekLow, List<String> reasons, List<String> warnings) {

        // Policy override, applied before anything else: a government-owned
        // company outside the financial sector is excluded regardless of how
        // well it scores. State-owned banks are the stated exception.
        if (isExcludedStateOwned(stock)) {
            warnings.add("State-owned and outside the financial sector — excluded by policy, "
                    + "whatever the score says");
            return "AVOID";
        }

        // Too little data to stand behind a call, however the blend landed.
        if (completeness < 50) {
            return "WATCH";
        }

        String base;
        if (overall >= 80) base = "STRONG_BUY";
        else if (overall >= 65) base = "BUY";
        else if (overall >= 50) base = "WATCH";
        else if (overall >= 35) base = "HOLD";
        else base = "AVOID";

        // A stock already good enough to buy, caught at the bottom of its
        // range, is the entry point this whole tool exists to find — so it
        // is promoted rather than left to the score's rounding.
        if ("BUY".equals(base) && nearFiftyTwoWeekLow) {
            reasons.add("Already a buy and trading near its 52-week low — upgraded to strong buy");
            return "STRONG_BUY";
        }

        return base;
    }

    /**
     * Government-owned and not a financial. Banks, insurers and other
     * financial-sector names stay eligible — the exclusion is aimed at
     * state-run industrials and utilities, not at public banks.
     */
    private boolean isNearFiftyTwoWeekLow(Stock stock) {
        BigDecimal position = stock.getPriceData() != null
                ? stock.getPriceData().getCloseTo52weekslowPct() : null;
        return position != null && position.compareTo(NEAR_LOW_THRESHOLD) >= 0;
    }

    private boolean isExcludedStateOwned(Stock stock) {
        if (stock.getOwnershipType() != OwnershipType.GOVERNMENT) {
            return false;
        }
        SectorType sector = stock.getSector();
        return !(sector == SectorType.BANKING
                || sector == SectorType.FINANCIALS
                || sector == SectorType.INSURANCE);
    }

    private int count(Integer... values) {
        int present = 0;
        for (Integer value : values) {
            if (value != null) present++;
        }
        return present;
    }

    private Integer average(List<Integer> parts) {
        if (parts.isEmpty()) {
            return null;
        }
        int sum = 0;
        for (int part : parts) {
            sum += part;
        }
        return (int) Math.round((double) sum / parts.size());
    }

    private int clamp(double value) {
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }

    /** Kept for callers that want the raw scale used by the range scorers. */
    static BigDecimal round2(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
