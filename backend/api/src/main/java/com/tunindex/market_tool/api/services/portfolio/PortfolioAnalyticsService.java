package com.tunindex.market_tool.api.services.portfolio;

import com.tunindex.market_tool.api.dto.portfolio.PortfolioAnalyticsDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioIncomeDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioPositionDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioSummaryDto;
import com.tunindex.market_tool.api.dto.portfolio.PortfolioWeightDto;
import com.tunindex.market_tool.api.dto.stock.StockResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Structural analysis of a portfolio: concentration, sector tilt, weighted
 * beta and projected income.
 *
 * <p>Not {@code @Transactional}. It reads the portfolio through
 * {@link PortfolioService} and then makes a blocking call to the collector for
 * the reference data — holding a pooled connection across that round trip is
 * what exhausted the Hikari pool once already in this service.
 *
 * <p>Weights are computed on the invested book, excluding cash. Cash is
 * reported separately: folding it into the weights would make an idle account
 * look beautifully diversified while owning nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioAnalyticsService {

    private static final String COLLECTOR_URL = "http://collector-service/internal/stock-data";

    private static final int MONEY_SCALE = 2;
    private static final int PCT_SCALE = 2;
    private static final int RATIO_SCALE = 4;

    /** HHI at or below this reads as a genuinely spread book. */
    private static final int HHI_DIVERSIFIED = 1500;

    /** HHI at or above this is a concentrated book by any standard reading. */
    private static final int HHI_CONCENTRATED = 2500;

    /** Any single position above this share of the book is worth calling out. */
    private static final BigDecimal SINGLE_POSITION_WARN_PCT = new BigDecimal("25");

    /** Any single sector above this share is worth calling out. */
    private static final BigDecimal SECTOR_WARN_PCT = new BigDecimal("40");

    /** Below this coverage, an average across the book is not worth reporting. */
    private static final BigDecimal MIN_COVERAGE_PCT = new BigDecimal("50");

    private final PortfolioService portfolioService;
    private final WebClient.Builder webClientBuilder;

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    public PortfolioAnalyticsDto analytics(Authentication authentication) {
        PortfolioSummaryDto summary = portfolioService.getPortfolio(authentication);
        List<PortfolioPositionDto> positions = summary.getPositions() == null
                ? List.of()
                : summary.getPositions();

        BigDecimal invested = summary.getTotalMarketValue() == null
                ? BigDecimal.ZERO
                : summary.getTotalMarketValue();
        BigDecimal cash = summary.getCashBalance() == null ? BigDecimal.ZERO : summary.getCashBalance();
        BigDecimal totalValue = invested.add(cash);

        List<String> observations = new ArrayList<>();

        if (positions.isEmpty() || invested.compareTo(BigDecimal.ZERO) <= 0) {
            observations.add("No open positions — every figure here needs at least one holding to describe.");
            return PortfolioAnalyticsDto.builder()
                    .positionCount(0)
                    .totalMarketValue(invested)
                    .cashBalance(cash)
                    .cashWeightPct(totalValue.compareTo(BigDecimal.ZERO) == 0
                            ? null
                            : percent(cash, totalValue))
                    .positionWeights(List.of())
                    .sectorWeights(List.of())
                    .incomeByPosition(List.of())
                    .observations(observations)
                    .build();
        }

        Map<String, StockResponseDto> reference = fetchReference(positions);

        List<PortfolioWeightDto> weights = positionWeights(positions, invested);
        List<PortfolioWeightDto> sectors = sectorWeights(positions, reference, invested);

        int hhi = herfindahl(weights);
        PortfolioWeightDto largestPosition = weights.get(0);
        PortfolioWeightDto largestSector = sectors.get(0);

        WeightedAverage beta = weightedBeta(positions, reference, invested);
        Income income = projectedIncome(positions, reference, invested);

        observations.add("Weights are computed on the invested book of "
                + invested.setScale(MONEY_SCALE, RoundingMode.HALF_UP) + " TND; the "
                + cash.setScale(MONEY_SCALE, RoundingMode.HALF_UP) + " TND in cash is reported separately.");

        if (largestPosition.getWeightPct().compareTo(SINGLE_POSITION_WARN_PCT) > 0) {
            observations.add(largestPosition.getKey() + " is " + largestPosition.getWeightPct()
                    + "% of the book — a bad quarter there moves the whole portfolio.");
        }
        if (largestSector.getWeightPct().compareTo(SECTOR_WARN_PCT) > 0) {
            observations.add(readableSector(largestSector.getKey()) + " accounts for "
                    + largestSector.getWeightPct() + "% across " + largestSector.getPositions()
                    + " holdings — these tend to fall together.");
        }
        if (hhi >= HHI_CONCENTRATED) {
            observations.add("Concentration index " + hhi + " — this book behaves like roughly "
                    + effectivePositions(hhi) + " equally-weighted positions, not " + positions.size() + ".");
        }
        if (beta.coveragePct().compareTo(MIN_COVERAGE_PCT) < 0) {
            observations.add("Beta is not reported: we only hold a beta for " + beta.coveragePct()
                    + "% of the book, too little to average honestly.");
        }
        if (income.coveragePct().compareTo(MIN_COVERAGE_PCT) < 0) {
            observations.add("Income projection covers " + income.coveragePct()
                    + "% of the book — the rest have no published yield, so the real figure is likely higher.");
        }

        return PortfolioAnalyticsDto.builder()
                .positionCount(positions.size())
                .totalMarketValue(invested)
                .cashBalance(cash)
                .cashWeightPct(totalValue.compareTo(BigDecimal.ZERO) == 0 ? null : percent(cash, totalValue))
                .concentrationHhi(hhi)
                .concentrationLabel(concentrationLabel(hhi))
                .effectivePositions(effectivePositions(hhi))
                .largestPositionPct(largestPosition.getWeightPct())
                .largestPositionSymbol(largestPosition.getKey())
                .largestSectorPct(largestSector.getWeightPct())
                .largestSectorName(readableSector(largestSector.getKey()))
                .positionWeights(weights)
                .sectorWeights(sectors)
                .weightedBeta(beta.coveragePct().compareTo(MIN_COVERAGE_PCT) < 0 ? null : beta.value())
                .betaCoveragePct(beta.coveragePct())
                .projectedAnnualIncome(income.total())
                .portfolioYieldPct(income.yieldPct())
                .incomeCoveragePct(income.coveragePct())
                .incomeByPosition(income.rows())
                .observations(observations)
                .build();
    }

    /**
     * One batched call for the reference data rather than one per holding.
     * A symbol we cannot resolve is simply absent from the map, and every
     * consumer below treats absence as "unknown" rather than substituting a
     * default that would quietly skew an average.
     */
    private Map<String, StockResponseDto> fetchReference(List<PortfolioPositionDto> positions) {
        String symbols = positions.stream()
                .map(PortfolioPositionDto::getSymbol)
                .collect(Collectors.joining(","));

        try {
            List<StockResponseDto> stocks = webClientBuilder.build()
                    .get()
                    .uri(COLLECTOR_URL + "/by-symbols?symbols={symbols}", symbols)
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<StockResponseDto>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (stocks == null) {
                return Map.of();
            }
            Map<String, StockResponseDto> bySymbol = new LinkedHashMap<>();
            for (StockResponseDto stock : stocks) {
                bySymbol.put(stock.getSymbol(), stock);
            }
            return bySymbol;
        } catch (RuntimeException ex) {
            // Analytics degrade to weights-only rather than failing outright:
            // concentration is computed from the portfolio alone and is still
            // worth showing when the reference lookup is unavailable.
            log.warn("Reference data unavailable for portfolio analytics: {}", ex.getMessage());
            return Map.of();
        }
    }

    private List<PortfolioWeightDto> positionWeights(List<PortfolioPositionDto> positions, BigDecimal invested) {
        return positions.stream()
                .map(position -> PortfolioWeightDto.builder()
                        .key(position.getSymbol())
                        .label(position.getName())
                        .marketValue(position.getMarketValue())
                        .weightPct(percent(position.getMarketValue(), invested))
                        .positions(1)
                        .build())
                .sorted(Comparator.comparing(PortfolioWeightDto::getWeightPct).reversed())
                .toList();
    }

    private List<PortfolioWeightDto> sectorWeights(List<PortfolioPositionDto> positions,
                                                   Map<String, StockResponseDto> reference,
                                                   BigDecimal invested) {
        Map<String, BigDecimal> valueBySector = new LinkedHashMap<>();
        Map<String, Integer> countBySector = new LinkedHashMap<>();

        for (PortfolioPositionDto position : positions) {
            StockResponseDto stock = reference.get(position.getSymbol());
            // "UNCLASSIFIED" rather than dropping the holding: its value is
            // real and must still add up to 100% of the book.
            String sector = stock != null && stock.getSector() != null
                    ? stock.getSector().name()
                    : "UNCLASSIFIED";
            valueBySector.merge(sector, position.getMarketValue(), BigDecimal::add);
            countBySector.merge(sector, 1, Integer::sum);
        }

        return valueBySector.entrySet().stream()
                .map(entry -> PortfolioWeightDto.builder()
                        .key(entry.getKey())
                        .label(readableSector(entry.getKey()))
                        .marketValue(entry.getValue())
                        .weightPct(percent(entry.getValue(), invested))
                        .positions(countBySector.get(entry.getKey()))
                        .build())
                .sorted(Comparator.comparing(PortfolioWeightDto::getWeightPct).reversed())
                .toList();
    }

    /** Sum of squared percentage weights — the standard HHI, 0..10000. */
    private int herfindahl(List<PortfolioWeightDto> weights) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PortfolioWeightDto weight : weights) {
            sum = sum.add(weight.getWeightPct().multiply(weight.getWeightPct()));
        }
        return sum.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** The inverse-HHI reading: how many equal positions this book behaves like. */
    private BigDecimal effectivePositions(int hhi) {
        if (hhi <= 0) {
            return null;
        }
        return BigDecimal.valueOf(10000)
                .divide(BigDecimal.valueOf(hhi), 1, RoundingMode.HALF_UP);
    }

    private String concentrationLabel(int hhi) {
        if (hhi <= HHI_DIVERSIFIED) {
            return "DIVERSIFIED";
        }
        return hhi >= HHI_CONCENTRATED ? "CONCENTRATED" : "MODERATE";
    }

    /**
     * Value-weighted beta over the holdings we have a beta for, with the
     * covered share reported alongside. Renormalising over the covered value
     * (rather than the whole book) keeps the average correct; the coverage
     * figure is what tells the reader how much to trust it.
     */
    private WeightedAverage weightedBeta(List<PortfolioPositionDto> positions,
                                         Map<String, StockResponseDto> reference,
                                         BigDecimal invested) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal covered = BigDecimal.ZERO;

        for (PortfolioPositionDto position : positions) {
            StockResponseDto stock = reference.get(position.getSymbol());
            if (stock == null || stock.getBeta() == null) {
                continue;
            }
            weightedSum = weightedSum.add(stock.getBeta().multiply(position.getMarketValue()));
            covered = covered.add(position.getMarketValue());
        }

        if (covered.compareTo(BigDecimal.ZERO) == 0) {
            return new WeightedAverage(null, BigDecimal.ZERO);
        }
        return new WeightedAverage(
                weightedSum.divide(covered, RATIO_SCALE, RoundingMode.HALF_UP),
                percent(covered, invested));
    }

    private Income projectedIncome(List<PortfolioPositionDto> positions,
                                   Map<String, StockResponseDto> reference,
                                   BigDecimal invested) {
        List<PortfolioIncomeDto> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal covered = BigDecimal.ZERO;

        for (PortfolioPositionDto position : positions) {
            StockResponseDto stock = reference.get(position.getSymbol());
            BigDecimal yield = stock == null ? null : stock.getDividendYield();

            BigDecimal projected = null;
            if (yield != null && yield.compareTo(BigDecimal.ZERO) > 0) {
                projected = position.getMarketValue()
                        .multiply(yield)
                        .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
                total = total.add(projected);
                covered = covered.add(position.getMarketValue());
            }

            rows.add(PortfolioIncomeDto.builder()
                    .symbol(position.getSymbol())
                    .name(position.getName())
                    .quantity(position.getQuantity())
                    .marketValue(position.getMarketValue())
                    .dividendYieldPct(yield)
                    .projectedAnnualIncome(projected)
                    .build());
        }

        rows.sort(Comparator.comparing(
                PortfolioIncomeDto::getProjectedAnnualIncome,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return new Income(
                total.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                // Yield is quoted against the whole invested book, not just the
                // paying names: that is the income the portfolio actually
                // produces per dinar invested, which is the useful figure.
                invested.compareTo(BigDecimal.ZERO) == 0 ? null : percent(total, invested),
                percent(covered, invested),
                rows);
    }

    private BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (part == null || whole == null || whole.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(PCT_SCALE);
        }
        return part.multiply(BigDecimal.valueOf(100))
                .divide(whole, PCT_SCALE, RoundingMode.HALF_UP);
    }

    /** SECTOR_ENUM_NAME -> "Sector Enum Name" for display. */
    private String readableSector(String raw) {
        if (raw == null) {
            return "Unclassified";
        }
        String[] words = raw.toLowerCase().split("_");
        StringBuilder readable = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (readable.length() > 0) {
                readable.append(' ');
            }
            readable.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return readable.toString();
    }

    private record WeightedAverage(BigDecimal value, BigDecimal coveragePct) {
    }

    private record Income(BigDecimal total,
                          BigDecimal yieldPct,
                          BigDecimal coveragePct,
                          List<PortfolioIncomeDto> rows) {
    }
}
