package com.tunindex.market_tool.collector.services.market;

import com.tunindex.market_tool.collector.dto.market.UnusualActivityDto;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.entities.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Flags names behaving unlike themselves today: volume far above their own
 * average, a price at the edge of its 52-week range, or an outsized move.
 *
 * <p>Thresholds are relative to each stock's own history rather than absolute.
 * On the BVMT an absolute rule is useless — 50,000 shares is a dead session
 * for BIAT and a stampede for a thin industrial name.
 *
 * <p>Every signal is a description of what happened, not a prediction. A
 * volume spike on a falling price and one on a rising price are both flagged,
 * because which of those matters depends on what the user is looking for.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnusualActivityService {

    /** Volume must be at least this multiple of the 3-month average to flag. */
    private static final BigDecimal VOLUME_SPIKE_MULTIPLE = new BigDecimal("2.5");

    /** A day move beyond this magnitude is unusual on this exchange. */
    private static final BigDecimal LARGE_MOVE_PCT = new BigDecimal("3.0");

    /** Within this much of the 52-week extreme counts as testing it. */
    private static final BigDecimal RANGE_EDGE_PCT = new BigDecimal("1.5");

    /** Intraday high-low spread beyond this share of price is a wide range. */
    private static final BigDecimal WIDE_RANGE_PCT = new BigDecimal("5.0");

    private static final int SCALE = 2;

    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public List<UnusualActivityDto> scan(int limit) {
        List<UnusualActivityDto> flagged = new ArrayList<>();

        for (Stock stock : stockRepository.findAll()) {
            if (stock.getPriceData() == null || stock.getPriceData().getLastPrice() == null) {
                continue;
            }
            flagged.addAll(signalsFor(stock));
        }

        return flagged.stream()
                .sorted(Comparator.comparing(UnusualActivityDto::getStrength).reversed())
                .limit(Math.min(Math.max(limit, 1), 100))
                .toList();
    }

    /**
     * A stock can raise several independent signals — a breakout on heavy
     * volume is two facts, and collapsing them into one would hide whichever
     * ranked lower.
     */
    private List<UnusualActivityDto> signalsFor(Stock stock) {
        List<UnusualActivityDto> signals = new ArrayList<>();

        BigDecimal last = stock.getPriceData().getLastPrice();
        BigDecimal prev = stock.getPriceData().getPrevClose();
        BigDecimal changePct = changePercent(last, prev);
        Long volume = stock.getVolumeData() != null ? stock.getVolumeData().getVolume() : null;
        Long avgVolume = stock.getVolumeData() != null ? stock.getVolumeData().getAvgVolume3m() : null;
        BigDecimal multiple = volumeMultiple(volume, avgVolume);

        if (multiple != null && multiple.compareTo(VOLUME_SPIKE_MULTIPLE) >= 0) {
            signals.add(base(stock, changePct, volume, avgVolume, multiple)
                    .signal("VOLUME_SPIKE")
                    .detail("Traded " + multiple.stripTrailingZeros().toPlainString()
                            + "x its 3-month average volume")
                    .strength(multiple)
                    .build());
        }

        BigDecimal high = stock.getPriceData().getWeek52High();
        BigDecimal low = stock.getPriceData().getWeek52Low();

        if (high != null && high.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal distance = percentDistance(last, high);
            if (distance.abs().compareTo(RANGE_EDGE_PCT) <= 0 || last.compareTo(high) >= 0) {
                signals.add(base(stock, changePct, volume, avgVolume, multiple)
                        .signal("BREAKOUT_52W_HIGH")
                        .detail(last.compareTo(high) >= 0
                                ? "Trading at a new 52-week high"
                                : "Within " + distance.abs() + "% of its 52-week high")
                        // Scaled so a range-edge signal ranks alongside a ~3x
                        // volume spike rather than always sorting below it.
                        .strength(new BigDecimal("3").subtract(distance.abs().min(new BigDecimal("1.5"))))
                        .build());
            }
        }

        if (low != null && low.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal distance = percentDistance(last, low);
            if (distance.abs().compareTo(RANGE_EDGE_PCT) <= 0 || last.compareTo(low) <= 0) {
                signals.add(base(stock, changePct, volume, avgVolume, multiple)
                        .signal("BREAKDOWN_52W_LOW")
                        .detail(last.compareTo(low) <= 0
                                ? "Trading at a new 52-week low"
                                : "Within " + distance.abs() + "% of its 52-week low")
                        .strength(new BigDecimal("3").subtract(distance.abs().min(new BigDecimal("1.5"))))
                        .build());
            }
        }

        if (changePct != null && changePct.abs().compareTo(LARGE_MOVE_PCT) >= 0) {
            signals.add(base(stock, changePct, volume, avgVolume, multiple)
                    .signal("LARGE_MOVE")
                    .detail("Moved " + changePct + "% on the day")
                    // Divided so a 6% move scores 2.0, comparable to a 2x
                    // volume spike, keeping the ranking on one scale.
                    .strength(changePct.abs().divide(new BigDecimal("3"), SCALE, RoundingMode.HALF_UP))
                    .build());
        }

        BigDecimal dayHigh = stock.getPriceData().getDayHigh();
        BigDecimal dayLow = stock.getPriceData().getDayLow();
        if (dayHigh != null && dayLow != null && last.compareTo(BigDecimal.ZERO) > 0
                && dayHigh.compareTo(dayLow) > 0) {
            BigDecimal range = dayHigh.subtract(dayLow)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(last, SCALE, RoundingMode.HALF_UP);
            if (range.compareTo(WIDE_RANGE_PCT) >= 0) {
                signals.add(base(stock, changePct, volume, avgVolume, multiple)
                        .signal("WIDE_RANGE")
                        .detail("Intraday range spanned " + range + "% of the current price")
                        .strength(range.divide(new BigDecimal("5"), SCALE, RoundingMode.HALF_UP))
                        .build());
            }
        }

        return signals;
    }

    private UnusualActivityDto.UnusualActivityDtoBuilder base(Stock stock,
                                                              BigDecimal changePct,
                                                              Long volume,
                                                              Long avgVolume,
                                                              BigDecimal multiple) {
        return UnusualActivityDto.builder()
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .sector(stock.getSector() != null ? stock.getSector().name() : null)
                .lastPrice(stock.getPriceData().getLastPrice())
                .changePct(changePct)
                .volume(volume)
                .avgVolume3m(avgVolume)
                .volumeMultiple(multiple)
                .week52High(stock.getPriceData().getWeek52High())
                .week52Low(stock.getPriceData().getWeek52Low());
    }

    private BigDecimal changePercent(BigDecimal last, BigDecimal prev) {
        if (last == null || prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return last.subtract(prev)
                .multiply(BigDecimal.valueOf(100))
                .divide(prev, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal volumeMultiple(Long volume, Long avgVolume) {
        if (volume == null || avgVolume == null || avgVolume <= 0) {
            return null;
        }
        return BigDecimal.valueOf(volume)
                .divide(BigDecimal.valueOf(avgVolume), SCALE, RoundingMode.HALF_UP);
    }

    /** Signed distance from {@code reference} to {@code value}, in percent. */
    private BigDecimal percentDistance(BigDecimal value, BigDecimal reference) {
        return value.subtract(reference)
                .multiply(BigDecimal.valueOf(100))
                .divide(reference, SCALE, RoundingMode.HALF_UP);
    }
}
