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

    // Re-weighted toward the technical read.
    //
    // The blend used to be 30/25/20/10/10/5, which put half the score on the
    // fundamentals and let a cheap, healthy company rank as a buy while its
    // chart was still falling. That is the complaint this answers: the tool is
    // meant to find the moment as well as the company, and the moment lives in
    // the timing component, which is where the reversal read sits.
    //
    // Timing and momentum together now carry 40% rather than 35%. The points
    // come from financial health and income, the two least time-sensitive
    // things measured here and so the cheapest to trim.
    //
    // Valuation and timing are deliberately equal, and that tie is the whole
    // statement: what you buy and when you buy it matter the same amount.
    // Putting timing above valuation would make this a momentum tool wearing a
    // value tool's clothes - a well-timed entry into a bad business is still a
    // bad business. Leaving valuation dominant is what produced the original
    // complaint, where a cheap, healthy company ranked as a buy while its
    // chart was still falling.
    private static final int WEIGHT_VALUATION = 30;
    private static final int WEIGHT_TIMING = 30;
    private static final int WEIGHT_FINANCIAL_HEALTH = 18;
    private static final int WEIGHT_INCOME = 7;
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
        return score(stock, technical, news, null, null);
    }

    public OpportunityScoreDto score(Stock stock, TechnicalAnalysisDto technical,
                                     List<NewsImpactDto> news, BigDecimal oneYearReturnPct) {
        return score(stock, technical, news, oneYearReturnPct, null);
    }

    /**
     * @param oneYearReturnPct 12-month return measured from stored price
     *                         history. The scraped {@code oneYearReturn}
     *                         field is empty for every tracked stock, so
     *                         without this the momentum component scored on
     *                         its trend signal alone and silently lost half
     *                         its inputs.
     */
    public OpportunityScoreDto score(Stock stock, TechnicalAnalysisDto technical,
                                     List<NewsImpactDto> news, BigDecimal oneYearReturnPct,
                                     ReversalDetector.ReversalSignal reversal) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Integer valuation = scoreValuation(stock, reasons, warnings);
        Integer timing = scoreTiming(stock, technical, reversal, reasons, warnings);
        Integer health = scoreFinancialHealth(stock, reasons, warnings);
        Integer income = scoreIncome(stock, reasons);
        Integer momentum = scoreMomentum(stock, technical, oneYearReturnPct, reasons, warnings);
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
                .verdict(verdict(overall, completeness, stock, isNearFiftyTwoWeekLow(stock),
                        reversal == null ? ReversalDetector.Phase.NEUTRAL : reversal.phase(),
                        reasons, warnings))
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
            //
            // A negative P/B means negative shareholders' equity - the company
            // owes more than it owns. That scores 0, the same as negative
            // earnings ten lines above, rather than the 50 it used to get:
            // scoring insolvency as "average" pulled six of the most distressed
            // companies on the exchange towards a neutral valuation mark, and
            // it contradicted this method's own treatment of a negative P/E.
            int pbScore = pb <= 0 ? 0 : clamp((4 - pb) / 3 * 100);
            parts.add(pbScore);
            if (pb > 0 && pb < 1) {
                reasons.add(String.format("Trades at %.2fx book value", pb));
            } else if (pb <= 0) {
                warnings.add("Negative book value — liabilities exceed assets");
            }
        }

        return average(parts);
    }

    // ── Timing: is now a good moment to enter? ─────────────────────────────

    private Integer scoreTiming(Stock stock, TechnicalAnalysisDto technical,
                                ReversalDetector.ReversalSignal reversal,
                                List<String> reasons, List<String> warnings) {
        BigDecimal closeTo52WeekLow = stock.getPriceData() != null
                ? stock.getPriceData().getCloseTo52weekslowPct() : null;
        return scoreTimingFrom(closeTo52WeekLow, technical, reversal, reasons, warnings);
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
                                   ReversalDetector.ReversalSignal reversal,
                                   List<String> reasons, List<String> warnings) {
        List<Integer> parts = new ArrayList<>();

        // The reversal read comes first and carries the most weight, because
        // it answers the question the rest only circle: has the decline
        // stopped? Cheapness and an oversold reading say a stock has fallen,
        // not that it has finished falling - and a falling stock stays
        // oversold the whole way down.
        //
        // Counted three times deliberately. Averaged once among five
        // indicators it would move the component by a couple of points, which
        // is not the difference between "wait" and "this is the entry".
        if (reversal != null && reversal.phase() != ReversalDetector.Phase.NEUTRAL) {
            int weightedReversal = reversal.score();
            parts.add(weightedReversal);
            parts.add(weightedReversal);
            parts.add(weightedReversal);

            switch (reversal.phase()) {
                case REVERSING -> reasons.add("Downtrend broken — " + String.join("; ", reversal.conditions()));
                case BOTTOMING -> reasons.add("Showing exhaustion after a decline — "
                        + String.join("; ", reversal.conditions()));
                case DOWNTREND -> warnings.add(
                        "Still falling with no reversal confirmed — cheap is not the same as bottomed");
                case TOPPING -> warnings.add("Extended and losing momentum");
                default -> { }
            }
        }

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

    private Integer scoreMomentum(Stock stock, TechnicalAnalysisDto technical, BigDecimal computedOneYearReturn,
                                  List<String> reasons, List<String> warnings) {
        List<Integer> parts = new ArrayList<>();

        BigDecimal stored = stock.getFundamentalData() != null
                ? stock.getFundamentalData().getOneYearReturn() : null;
        // Prefer the figure measured from real stored closes; fall back to
        // the scraped field on the chance it is ever populated.
        BigDecimal oneYearReturn = computedOneYearReturn != null ? computedOneYearReturn : stored;
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
                           boolean nearFiftyTwoWeekLow, ReversalDetector.Phase phase,
                           List<String> reasons, List<String> warnings) {

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

        // A stock still falling is not a buy at any score.
        //
        // The blend could previously carry a cheap, healthy company to BUY
        // while its chart was in an unbroken downtrend - and the analyst,
        // reading the same data, would tell the reader to stay out. Two of our
        // own components contradicting each other on the same screen is worse
        // than either being wrong: it leaves the reader to arbitrate.
        //
        // Capped at WATCH rather than pushed to AVOID, because the company may
        // well be worth owning; it is the timing that is wrong, and WATCH is
        // exactly that statement.
        if (phase == ReversalDetector.Phase.DOWNTREND || phase == ReversalDetector.Phase.TOPPING) {
            if ("STRONG_BUY".equals(base) || "BUY".equals(base)) {
                warnings.add(phase == ReversalDetector.Phase.DOWNTREND
                        ? "Scores well but is still in a downtrend — held at watch until the fall ends"
                        : "Scores well but the move is extended — held at watch rather than chased");
                return "WATCH";
            }
        }

        // A stock already good enough to buy, caught at the bottom of its
        // range, is the entry point this whole tool exists to find — so it
        // is promoted rather than left to the score's rounding.
        //
        // Only once the decline has actually ended: "near the low" in an
        // unbroken downtrend is the trap the cap above exists to avoid, and
        // promoting on it would reintroduce the very thing it prevents.
        // And only while the price is still below what the business is worth.
        // "At its 52-week low" and "undervalued" are different claims, and
        // conflating them promoted ARTES to STRONG_BUY at 10.51 against a fair
        // value of 10.33 - the analyst, reading the same two numbers, was
        // simultaneously reporting that no entry existed at any price. A stock
        // can make a new low and still be expensive.
        if ("BUY".equals(base) && nearFiftyTwoWeekLow && isBelowFairValue(stock)) {
            reasons.add("Already a buy, trading near its 52-week low and still below fair value "
                    + "— upgraded to strong buy");
            return "STRONG_BUY";
        }

        return base;
    }

    /**
     * Government-owned and not a financial. Banks, insurers and other
     * financial-sector names stay eligible — the exclusion is aimed at
     * state-run industrials and utilities, not at public banks.
     */
    /**
     * Price still under the Graham fair value.
     *
     * <p>False when no fair value could be computed, so a stock we cannot
     * value is never promoted on a claim we cannot support.
     */
    private boolean isBelowFairValue(Stock stock) {
        BigDecimal fairValue = stock.getCalculatedValues() != null
                ? stock.getCalculatedValues().getGrahamFairValue() : null;
        BigDecimal price = stock.getPriceData() != null
                ? stock.getPriceData().getLastPrice() : null;
        return fairValue != null && price != null
                && fairValue.signum() > 0 && price.compareTo(fairValue) < 0;
    }

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
