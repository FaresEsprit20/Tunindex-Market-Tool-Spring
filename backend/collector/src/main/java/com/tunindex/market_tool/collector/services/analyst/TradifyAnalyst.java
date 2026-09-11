package com.tunindex.market_tool.collector.services.analyst;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.dto.analyst.TradeSetupDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.services.scoring.ReversalDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns indicators into the decision they exist to support: what price to pay.
 *
 * <p>A score says a stock is worth owning. It does not say whether today is the
 * day, and those are different questions - a good business bought at the top of
 * a run is a bad position. What was missing was the level: the band where entry
 * is attractive, the point where the idea is wrong, and what it is worth if it
 * works.
 *
 * <p>It also reads the indicators <em>in context</em> rather than one at a
 * time. "RSI 28, oversold" is not a buy signal; it is as common halfway down a
 * decline as it is at the bottom. What distinguishes the two is everything
 * around it - how long the stock has been falling, whether the falling has
 * stopped, whether the structure has actually broken upward. That is what
 * {@link ReversalDetector} establishes and what this reads from.
 *
 * <p>Nothing here is fabricated. Every level is anchored to a real observation
 * - a band, a moving average, a swing low, a fair value - and when the anchors
 * are missing the field is null and the stance is {@code NO_SETUP}. A made-up
 * entry price is worse than none, because somebody would trade on it.
 */
@Service
@Slf4j
public class TradifyAnalyst {

    /** Below this many bars the structure is not worth reading. */
    private static final int MIN_BARS = 30;

    /** Lookback for the swing low that anchors the entry band. */
    private static final int SWING_LOOKBACK = 20;

    /**
     * Margin demanded against fair value before a price is called attractive.
     *
     * <p>The entry band is capped here, so the analyst never describes a price
     * as a good entry simply because the chart bounced there. Paying fair value
     * leaves nothing for being wrong.
     */
    private static final BigDecimal FAIR_VALUE_DISCOUNT = new BigDecimal("0.85");

    /** Stop sits this many average ranges below the structure it rests on. */
    private static final BigDecimal STOP_ATR_MULTIPLE = new BigDecimal("1.0");

    public TradeSetupDto analyse(Stock stock, TechnicalAnalysisDto technical,
                                 List<PriceHistory> historyAscending,
                                 ReversalDetector.ReversalSignal reversal) {

        String symbol = stock == null ? null : stock.getSymbol();
        List<PriceHistory> bars = historyAscending == null ? List.of() : historyAscending;

        BigDecimal price = technical == null ? null : technical.getLastClose();
        if (price == null && stock != null && stock.getPriceData() != null) {
            price = stock.getPriceData().getLastPrice();
        }

        if (price == null || price.signum() <= 0 || bars.size() < MIN_BARS) {
            return TradeSetupDto.builder()
                    .symbol(symbol)
                    // Null-safe: this branch is reached precisely when the
                    // price may be missing, and a missing price should read as
                    // unknown rather than as zero.
                    .lastPrice(round(price))
                    .stance(TradeSetupDto.Stance.NO_SETUP)
                    .headline("Not enough trading history to place an entry")
                    .phase(reversal == null ? null : reversal.phase().name())
                    .evidence(List.of())
                    .risks(List.of("Fewer than " + MIN_BARS + " bars of price history"))
                    .confidence(0)
                    .build();
        }

        List<String> evidence = new ArrayList<>();
        List<String> risks = new ArrayList<>();

        BigDecimal swingLow = swingLow(bars, SWING_LOOKBACK);
        BigDecimal atr = technical == null ? null : technical.getAtr14();
        BigDecimal fairValue = stock != null && stock.getCalculatedValues() != null
                ? stock.getCalculatedValues().getGrahamFairValue()
                : null;

        // ── The entry band ───────────────────────────────────────────────
        BigDecimal zoneLow = highestSupportBelow(price, technical, swingLow, evidence);
        if (zoneLow == null) {
            // No observed floor beneath the price. Rather than invent one,
            // step down by a measured amount of the stock's own volatility.
            zoneLow = atr == null ? null : price.subtract(atr.multiply(new BigDecimal("1.5")));
            if (zoneLow != null) {
                evidence.add("No support level below the price; band set one and a half "
                        + "average daily ranges beneath it");
            }
        }

        BigDecimal zoneHigh = null;
        if (zoneLow != null) {
            // One average daily range wide: narrow enough to be an instruction,
            // wide enough that a normal day's movement does not invalidate it.
            BigDecimal width = atr != null && atr.signum() > 0
                    ? atr
                    : price.multiply(new BigDecimal("0.03"));
            zoneHigh = zoneLow.add(width);

            // Never call a price attractive above fair value less a margin.
            // This is where the fundamental view constrains the chart: a bounce
            // off support is not a reason to pay more than the business is
            // worth.
            if (fairValue != null && fairValue.signum() > 0) {
                BigDecimal cap = fairValue.multiply(FAIR_VALUE_DISCOUNT);
                if (cap.compareTo(zoneHigh) < 0) {
                    zoneHigh = cap;
                    evidence.add("Band capped at a 15% discount to fair value of "
                            + money(fairValue));
                }
                if (zoneHigh.compareTo(zoneLow) <= 0) {
                    // Fair value sits below every chart support: there is no
                    // price here that is both technically and fundamentally
                    // attractive. Quoting the support level anyway would
                    // recommend a price we have just called too expensive,
                    // which is the opposite of what the cap is for.
                    risks.add("Fair value of " + money(fairValue)
                            + " sits below chart support - there is no entry here that is "
                            + "cheap on the numbers");
                    zoneLow = null;
                    zoneHigh = null;
                }
            }
        }

        boolean inZone = zoneLow != null && zoneHigh != null
                && price.compareTo(zoneLow) >= 0 && price.compareTo(zoneHigh) <= 0;

        BigDecimal distanceToZone = null;
        if (zoneHigh != null && price.compareTo(zoneHigh) > 0) {
            distanceToZone = price.subtract(zoneHigh)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(price, 2, RoundingMode.HALF_UP);
        }

        // ── Objectives and invalidation ──────────────────────────────────
        BigDecimal zoneMid = zoneLow != null && zoneHigh != null
                ? zoneLow.add(zoneHigh).divide(BigDecimal.valueOf(2), 3, RoundingMode.HALF_UP)
                : null;

        BigDecimal target1 = nearestResistanceAbove(price, technical, fairValue);
        BigDecimal target2 = fairValue != null && fairValue.signum() > 0
                && (target1 == null || fairValue.compareTo(target1) > 0)
                ? fairValue
                : technical == null ? null : technical.getBollingerUpper();

        BigDecimal stop = null;
        if (swingLow != null) {
            BigDecimal buffer = atr != null && atr.signum() > 0
                    ? atr.multiply(STOP_ATR_MULTIPLE)
                    : swingLow.multiply(new BigDecimal("0.05"));
            stop = swingLow.subtract(buffer);

            // A stop has to sit below the band it protects. When the swing low
            // is above the support the band was drawn on, the subtraction can
            // land above the entry - a stop that would be hit the moment the
            // position is opened, which is not a stop at all. Anchor it under
            // the band instead.
            if (zoneLow != null && stop.compareTo(zoneLow) >= 0) {
                stop = zoneLow.subtract(buffer);
            }
            if (stop.signum() < 0) {
                stop = null;
            }
        }

        BigDecimal expectedReturn = null;
        BigDecimal riskReward = null;
        if (zoneMid != null && zoneMid.signum() > 0 && target1 != null && target1.compareTo(zoneMid) > 0) {
            expectedReturn = target1.subtract(zoneMid)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(zoneMid, 2, RoundingMode.HALF_UP);

            if (stop != null && zoneMid.compareTo(stop) > 0) {
                BigDecimal risk = zoneMid.subtract(stop);
                if (risk.signum() > 0) {
                    riskReward = target1.subtract(zoneMid).divide(risk, 2, RoundingMode.HALF_UP);
                }
            }
        }

        // ── Reading it in context ────────────────────────────────────────
        ReversalDetector.Phase phase = reversal == null ? ReversalDetector.Phase.NEUTRAL : reversal.phase();
        String regime = describeRegime(bars, technical, phase, reversal);
        if (reversal != null) {
            evidence.addAll(reversal.conditions());
        }
        collectRisks(technical, phase, riskReward, risks);

        TradeSetupDto.Stance stance = decideStance(phase, inZone, distanceToZone, zoneLow);

        return TradeSetupDto.builder()
                .symbol(symbol)
                .lastPrice(round(price))
                .stance(stance)
                .headline(headline(stance, zoneLow, zoneHigh, distanceToZone))
                .buyZoneLow(round(zoneLow))
                .buyZoneHigh(round(zoneHigh))
                .priceInBuyZone(inZone)
                .distanceToZonePct(distanceToZone)
                .buyZoneBasis(basis(zoneLow, zoneHigh, technical, swingLow, fairValue))
                .target1(round(target1))
                .target2(round(target2))
                .stopLevel(round(stop))
                .expectedReturnPct(expectedReturn)
                .riskReward(riskReward)
                .phase(phase.name())
                .regimeSummary(regime)
                .evidence(List.copyOf(evidence))
                .risks(List.copyOf(risks))
                .confidence(confidence(technical, bars, zoneLow, target1, stop))
                .build();
    }

    // ── Levels ───────────────────────────────────────────────────────────

    /**
     * The nearest real floor beneath today's price.
     *
     * <p>"Nearest" matters: the closest support is the one the price will test
     * first, and a band drawn at a level far below is an instruction nobody can
     * act on. Candidates are only ever levels the data actually produced.
     */
    private BigDecimal highestSupportBelow(BigDecimal price, TechnicalAnalysisDto technical,
                                           BigDecimal swingLow, List<String> evidence) {
        BigDecimal best = null;
        String label = null;

        if (technical != null) {
            best = better(best, technical.getBollingerLower(), price);
            if (best != null && best.equals(technical.getBollingerLower())) {
                label = "the lower Bollinger band";
            }
            BigDecimal sma50 = technical.getSma50();
            BigDecimal candidate = better(best, sma50, price);
            if (candidate != null && candidate.equals(sma50) && !candidate.equals(best)) {
                label = "the 50-day average";
            }
            best = candidate;
        }
        BigDecimal withSwing = better(best, swingLow, price);
        if (withSwing != null && withSwing.equals(swingLow) && !withSwing.equals(best)) {
            label = "the recent swing low";
        }
        best = withSwing;

        if (best != null && label != null) {
            evidence.add("Nearest support is " + label + " at " + money(best));
        }
        return best;
    }

    /** Whichever candidate is higher, provided it is still below the price. */
    private BigDecimal better(BigDecimal current, BigDecimal candidate, BigDecimal price) {
        if (candidate == null || candidate.signum() <= 0 || candidate.compareTo(price) >= 0) {
            return current;
        }
        return current == null || candidate.compareTo(current) > 0 ? candidate : current;
    }

    /** The first level the price has to clear on the way up. */
    private BigDecimal nearestResistanceAbove(BigDecimal price, TechnicalAnalysisDto technical,
                                              BigDecimal fairValue) {
        BigDecimal best = null;
        if (technical != null) {
            best = lower(best, technical.getSma50(), price);
            best = lower(best, technical.getBollingerMiddle(), price);
            best = lower(best, technical.getBollingerUpper(), price);
        }
        best = lower(best, fairValue, price);
        return best;
    }

    private BigDecimal lower(BigDecimal current, BigDecimal candidate, BigDecimal price) {
        if (candidate == null || candidate.signum() <= 0 || candidate.compareTo(price) <= 0) {
            return current;
        }
        return current == null || candidate.compareTo(current) < 0 ? candidate : current;
    }

    private BigDecimal swingLow(List<PriceHistory> bars, int lookback) {
        return bars.stream()
                .skip(Math.max(0, bars.size() - lookback))
                .map(PriceHistory::getLow)
                .filter(java.util.Objects::nonNull)
                .filter(v -> v.signum() > 0)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    // ── Narrative ────────────────────────────────────────────────────────

    /**
     * What the stock has been doing, not just what it is doing today.
     *
     * <p>This is the context an indicator reading cannot carry on its own. A
     * stock that has fallen for seven months and has now stopped falling is a
     * different proposition from one that dipped last week, and the RSI can
     * read identically for both.
     */
    private String describeRegime(List<PriceHistory> bars, TechnicalAnalysisDto technical,
                                  ReversalDetector.Phase phase,
                                  ReversalDetector.ReversalSignal reversal) {
        StringBuilder text = new StringBuilder();

        BigDecimal peak = bars.stream().map(PriceHistory::getClose)
                .filter(java.util.Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
        BigDecimal last = bars.isEmpty() ? null : bars.get(bars.size() - 1).getClose();

        if (peak != null && last != null && peak.signum() > 0 && peak.compareTo(last) > 0) {
            BigDecimal drawdown = peak.subtract(last)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(peak, 1, RoundingMode.HALF_UP);
            text.append("Down ").append(drawdown).append("% from its high over the ")
                    .append(bars.size()).append(" sessions on record. ");
        } else if (peak != null && last != null && last.compareTo(peak) >= 0) {
            text.append("Trading at the top of its recorded range. ");
        }

        long belowSma50 = 0;
        if (technical != null && technical.getSma50() != null) {
            BigDecimal sma50 = technical.getSma50();
            belowSma50 = bars.stream().skip(Math.max(0, bars.size() - 60))
                    .map(PriceHistory::getClose)
                    .filter(java.util.Objects::nonNull)
                    .filter(c -> c.compareTo(sma50) < 0)
                    .count();
            if (belowSma50 > 40) {
                text.append("It has spent most of the last three months below its 50-day average. ");
            } else if (belowSma50 < 10) {
                text.append("It has held above its 50-day average for most of the last three months. ");
            }
        }

        text.append(switch (phase) {
            case DOWNTREND -> "The decline is still in force and no floor has formed - "
                    + "oversold readings here are a symptom, not an entry.";
            case BOTTOMING -> "The fall has stopped making new lows and selling pressure is easing, "
                    + "but the turn is not confirmed. This is the watching stage.";
            case REVERSING -> "The downtrend has broken and momentum is confirming it. "
                    + "This is the window an entry is meant for.";
            case UPTREND -> "The advance is already under way, so the favourable entry has passed; "
                    + "what is left is waiting for a pullback.";
            case TOPPING -> "The move is extended and momentum is fading - the risk now sits on "
                    + "the downside.";
            case NEUTRAL -> "There is no clear structure to read at the moment.";
        });

        if (reversal != null && reversal.score() > 0) {
            text.append(" Reversal evidence scores ").append(reversal.score()).append("/100.");
        }
        return text.toString().trim();
    }

    private void collectRisks(TechnicalAnalysisDto technical, ReversalDetector.Phase phase,
                              BigDecimal riskReward, List<String> risks) {
        if (phase == ReversalDetector.Phase.DOWNTREND) {
            risks.add("Still in a downtrend - support levels give way in a falling market");
        }
        if (phase == ReversalDetector.Phase.BOTTOMING) {
            risks.add("The turn is not confirmed; a basing pattern can resume falling");
        }
        if (technical != null && technical.getAdx14() != null
                && technical.getAdx14().compareTo(new BigDecimal("20")) < 0) {
            risks.add("Trend strength is weak (ADX " + technical.getAdx14()
                    + ") - levels hold less reliably in a directionless market");
        }
        if (technical != null && technical.getVolatilityAnnualizedPct() != null
                && technical.getVolatilityAnnualizedPct().compareTo(new BigDecimal("60")) > 0) {
            risks.add("Highly volatile (" + technical.getVolatilityAnnualizedPct()
                    + "% annualised) - position sizes should reflect that");
        }
        if (riskReward != null && riskReward.compareTo(BigDecimal.ONE) < 0) {
            risks.add("Reward does not currently justify the risk (" + riskReward + ":1)");
        }
    }

    // ── Verdict ──────────────────────────────────────────────────────────

    private TradeSetupDto.Stance decideStance(ReversalDetector.Phase phase, boolean inZone,
                                              BigDecimal distanceToZone, BigDecimal zoneLow) {
        if (zoneLow == null) {
            return TradeSetupDto.Stance.NO_SETUP;
        }
        return switch (phase) {
            case DOWNTREND, TOPPING -> TradeSetupDto.Stance.HOLD_OFF;
            case BOTTOMING -> TradeSetupDto.Stance.WAIT_FOR_CONFIRMATION;
            // The one case where buying now is the instruction: the turn is
            // confirmed and the price is still inside the band.
            case REVERSING -> inZone
                    ? TradeSetupDto.Stance.ACCUMULATE_NOW
                    : TradeSetupDto.Stance.BUY_THE_DIP;
            case UPTREND -> inZone
                    ? TradeSetupDto.Stance.ACCUMULATE_NOW
                    : TradeSetupDto.Stance.BUY_THE_DIP;
            case NEUTRAL -> inZone
                    ? TradeSetupDto.Stance.WAIT_FOR_CONFIRMATION
                    : TradeSetupDto.Stance.HOLD_OFF;
        };
    }

    private String headline(TradeSetupDto.Stance stance, BigDecimal low, BigDecimal high,
                            BigDecimal distance) {
        String band = low == null || high == null ? null : money(low) + " - " + money(high);
        return switch (stance) {
            case ACCUMULATE_NOW -> band == null
                    ? "Accumulate at the current price"
                    : "Accumulate " + band + " - the price is in the buy zone now";
            case BUY_THE_DIP -> band == null
                    ? "Worth owning, but wait for a better price"
                    : "Wait for " + band
                    + (distance == null ? "" : " - about " + distance + "% below here");
            case WAIT_FOR_CONFIRMATION -> band == null
                    ? "Basing - wait for the turn to confirm"
                    : "Watch " + band + " - basing, but not confirmed yet";
            case HOLD_OFF -> "Hold off - the trend does not support an entry here";
            case NO_SETUP -> "Not enough trading history to place an entry";
        };
    }

    private String basis(BigDecimal low, BigDecimal high, TechnicalAnalysisDto technical,
                         BigDecimal swingLow, BigDecimal fairValue) {
        if (low == null || high == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (technical != null && low.equals(technical.getBollingerLower())) {
            parts.add("lower Bollinger band");
        }
        if (technical != null && low.equals(technical.getSma50())) {
            parts.add("50-day moving average");
        }
        if (low.equals(swingLow)) {
            parts.add("recent swing low");
        }
        if (technical != null && technical.getAtr14() != null) {
            parts.add("band one average daily range wide");
        }
        if (fairValue != null) {
            parts.add("capped below fair value");
        }
        return parts.isEmpty() ? null : "Anchored on the " + String.join(", ", parts);
    }

    /**
     * How much of this rests on data we have rather than absence.
     *
     * <p>Reported rather than hidden, because a setup built on three of six
     * inputs should not look the same as one built on all six.
     */
    private int confidence(TechnicalAnalysisDto technical, List<PriceHistory> bars,
                           BigDecimal zoneLow, BigDecimal target, BigDecimal stop) {
        int score = 0;
        if (bars.size() >= 120) score += 25; else if (bars.size() >= 60) score += 15;
        if (zoneLow != null) score += 25;
        if (target != null) score += 20;
        if (stop != null) score += 10;
        if (technical != null && technical.getAdx14() != null) score += 10;
        if (technical != null && technical.getAtr14() != null) score += 10;
        return Math.min(100, score);
    }

    private BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal value) {
        return value == null ? "?" : value.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }
}
