package com.tunindex.market_tool.collector.services.market;

import com.tunindex.market_tool.collector.dto.market.MarketBreadthDto;
import com.tunindex.market_tool.collector.dto.market.MarketMoverDto;
import com.tunindex.market_tool.collector.dto.market.SectorPerformanceDto;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.entities.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes whole-market breadth from the quotes already in our database.
 *
 * <p>Deliberately not cached and not scheduled: it is a single pass over the
 * ~69 rows we already hold, so recomputing on request is cheaper than
 * reasoning about staleness. That trade-off would flip at a few thousand names.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketBreadthService {

    private static final int MOVERS_LIMIT = 5;
    private static final int SCALE = 2;

    /**
     * A move counts as unchanged only when it is exactly flat. No epsilon:
     * BVMT quotes to the millime, so a 0.01% move is a real tick rather than
     * floating-point noise.
     */
    private static final BigDecimal FLAT = BigDecimal.ZERO;

    private final StockRepository stockRepository;

    @Transactional(readOnly = true)
    public MarketBreadthDto breadth() {
        List<Stock> stocks = stockRepository.findAll();

        int advancing = 0;
        int declining = 0;
        int unchanged = 0;
        int notPriced = 0;
        long totalVolume = 0L;
        BigDecimal changeSum = BigDecimal.ZERO;
        int pricedCount = 0;
        LocalDateTime asOf = null;

        List<MarketMoverDto> priced = new ArrayList<>();
        // Insertion-ordered so sectors come back stably run to run, which keeps
        // the sector strip in the UI from reshuffling on every poll.
        Map<String, SectorAccumulator> sectors = new LinkedHashMap<>();

        for (Stock stock : stocks) {
            String sectorName = stock.getSector() != null ? stock.getSector().name() : "UNCLASSIFIED";
            SectorAccumulator sector = sectors.computeIfAbsent(sectorName, key -> new SectorAccumulator());
            sector.total++;

            BigDecimal changePct = changePercent(stock);
            if (changePct == null) {
                notPriced++;
                continue;
            }

            pricedCount++;
            changeSum = changeSum.add(changePct);

            int direction = changePct.compareTo(FLAT);
            if (direction > 0) {
                advancing++;
                sector.advancing++;
            } else if (direction < 0) {
                declining++;
                sector.declining++;
            } else {
                unchanged++;
            }

            sector.priced++;
            sector.changeSum = sector.changeSum.add(changePct);

            Long volume = stock.getVolumeData() != null ? stock.getVolumeData().getVolume() : null;
            if (volume != null) {
                totalVolume += volume;
            }

            // Freshest live quote, not freshest row write: the latter is set
            // even when the exchange fetch failed.
            LocalDateTime quoteAt = stock.getPriceData().getLiveQuoteAt();
            if (quoteAt != null && (asOf == null || quoteAt.isAfter(asOf))) {
                asOf = quoteAt;
            }

            priced.add(MarketMoverDto.builder()
                    .symbol(stock.getSymbol())
                    .name(stock.getName())
                    .sector(sectorName)
                    .exchange(stock.getExchange())
                    .lastPrice(stock.getPriceData().getLastPrice())
                    .prevClose(stock.getPriceData().getPrevClose())
                    .changePct(changePct)
                    .volume(volume)
                    .build());
        }

        return MarketBreadthDto.builder()
                .advancing(advancing)
                .declining(declining)
                .unchanged(unchanged)
                .notPriced(notPriced)
                .total(stocks.size())
                .averageChangePct(pricedCount == 0
                        ? null
                        : changeSum.divide(BigDecimal.valueOf(pricedCount), SCALE, RoundingMode.HALF_UP))
                .totalVolume(totalVolume)
                .topGainers(movers(priced, true))
                .topLosers(movers(priced, false))
                .mostActive(mostActive(priced))
                .sectorPerformance(sectorPerformance(sectors))
                .asOf(asOf)
                .build();
    }

    /**
     * Day change from the two prices we store. Returns null — never zero —
     * when either side is missing or the previous close is zero, so a name we
     * cannot price is reported as unpriced instead of counted as flat.
     */
    private BigDecimal changePercent(Stock stock) {
        if (stock.getPriceData() == null) {
            return null;
        }
        if (!QuoteFreshness.isFresh(stock)) {
            return null;
        }
        BigDecimal last = stock.getPriceData().getLastPrice();
        BigDecimal prev = stock.getPriceData().getPrevClose();
        if (last == null || prev == null || prev.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return last.subtract(prev)
                .multiply(BigDecimal.valueOf(100))
                .divide(prev, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Movers lists only ever contain names that actually moved the right way.
     * A "top gainers" table padded out with flat and falling stocks on a down
     * day is worse than a short one.
     */
    private List<MarketMoverDto> movers(List<MarketMoverDto> priced, boolean gainers) {
        Comparator<MarketMoverDto> order = Comparator.comparing(MarketMoverDto::getChangePct);
        return priced.stream()
                .filter(mover -> gainers
                        ? mover.getChangePct().compareTo(FLAT) > 0
                        : mover.getChangePct().compareTo(FLAT) < 0)
                .sorted(gainers ? order.reversed() : order)
                .limit(MOVERS_LIMIT)
                .toList();
    }

    private List<MarketMoverDto> mostActive(List<MarketMoverDto> priced) {
        return priced.stream()
                .filter(mover -> mover.getVolume() != null && mover.getVolume() > 0)
                .sorted(Comparator.comparing(MarketMoverDto::getVolume).reversed())
                .limit(MOVERS_LIMIT)
                .toList();
    }

    /**
     * Sectors ordered best to worst. A sector with no priced name still comes
     * back, with a null average, rather than vanishing — the UI renders that as
     * "no data", which is information; a missing row would read as though the
     * sector did not exist.
     */
    private List<SectorPerformanceDto> sectorPerformance(Map<String, SectorAccumulator> sectors) {
        return sectors.entrySet().stream()
                .map(entry -> {
                    SectorAccumulator acc = entry.getValue();
                    BigDecimal average = acc.priced == 0
                            ? null
                            : acc.changeSum.divide(BigDecimal.valueOf(acc.priced), SCALE, RoundingMode.HALF_UP);
                    return SectorPerformanceDto.builder()
                            .sector(entry.getKey())
                            .averageChangePct(average)
                            .advancing(acc.advancing)
                            .declining(acc.declining)
                            .priced(acc.priced)
                            .total(acc.total)
                            .build();
                })
                .sorted(Comparator.comparing(
                        SectorPerformanceDto::getAverageChangePct,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static final class SectorAccumulator {
        private BigDecimal changeSum = BigDecimal.ZERO;
        private int advancing;
        private int declining;
        private int priced;
        private int total;
    }
}
