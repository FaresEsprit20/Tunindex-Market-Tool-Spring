package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.dto.risk.CorrelationMatrixDto;
import com.tunindex.market_tool.collector.dto.risk.RiskMetricsDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.risk.RiskAnalyticsService;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RiskAnalyticsService")
class RiskAnalyticsServiceTest {

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private RiskAnalyticsService service;

    @BeforeEach
    void setUp() {
        // Normally injected from market-tool.risk.risk-free-rate-pct; pinned
        // here so the ratio assertions do not move when the property changes.
        ReflectionTestUtils.setField(service, "riskFreeRatePct", new BigDecimal("8.0"));
        when(stockRepository.findBySymbol(any())).thenReturn(Optional.of(Stock.builder().symbol("TEST").build()));
    }

    /** A series that alternates +10% and -9.0909% returns exactly to its start. */
    private List<PriceHistory> alternating(String symbol, int points) {
        List<PriceHistory> history = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        double close = 100;
        for (int i = 0; i < points; i++) {
            history.add(PriceHistory.builder()
                    .symbol(symbol)
                    .tradeDate(date.plusDays(i))
                    .close(BigDecimal.valueOf(close))
                    .build());
            close = i % 2 == 0 ? close * 1.10 : close / 1.10;
        }
        return history;
    }

    private List<PriceHistory> flat(String symbol, int points) {
        List<PriceHistory> history = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < points; i++) {
            history.add(PriceHistory.builder()
                    .symbol(symbol)
                    .tradeDate(date.plusDays(i))
                    .close(new BigDecimal("100"))
                    .build());
        }
        return history;
    }

    @Test
    @DisplayName("refuses to report figures below the minimum sample, and says why")
    void refusesTooShortSeries() {
        when(priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(any(), any()))
                .thenReturn(flat("TEST", 5));

        RiskMetricsDto metrics = service.riskMetrics("TEST", 365);

        // Null, never zero: zero would read as "this stock does not move".
        assertThat(metrics.getAnnualisedVolatilityPct()).isNull();
        assertThat(metrics.getSharpeRatio()).isNull();
        assertThat(metrics.getMethodology())
                .anyMatch(line -> line.contains("would be noise"));
    }

    @Test
    @DisplayName("a never-moving series has zero volatility and no Sharpe ratio")
    void flatSeriesHasNoRatio() {
        when(priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(any(), any()))
                .thenReturn(flat("TEST", 60));
        when(priceHistoryRepository.findBySymbolInAndTradeDateGreaterThanEqualOrderBySymbolAscTradeDateAsc(
                anyList(), any())).thenReturn(List.of());
        when(stockRepository.findAll()).thenReturn(List.of(Stock.builder().symbol("TEST").build()));

        RiskMetricsDto metrics = service.riskMetrics("TEST", 365);

        assertThat(metrics.getAnnualisedVolatilityPct()).isEqualByComparingTo("0.00");
        // Dividing by zero volatility would be an infinite Sharpe; null is honest.
        assertThat(metrics.getSharpeRatio()).isNull();
        assertThat(metrics.getMaxDrawdownPct()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("max drawdown measures peak to trough, not first to last")
    void drawdownIsPeakToTrough() {
        // Rises to 120, falls to 90, recovers to 110: the drawdown is -25%
        // from the 120 peak, even though the series ends up on the period.
        List<PriceHistory> history = new ArrayList<>();
        double[] closes = {100, 110, 120, 105, 90, 100, 110};
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < 40; i++) {
            history.add(PriceHistory.builder()
                    .symbol("TEST")
                    .tradeDate(date.plusDays(i))
                    .close(BigDecimal.valueOf(i < closes.length ? closes[i] : 110))
                    .build());
        }
        when(priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(any(), any()))
                .thenReturn(history);
        when(priceHistoryRepository.findBySymbolInAndTradeDateGreaterThanEqualOrderBySymbolAscTradeDateAsc(
                anyList(), any())).thenReturn(List.of());
        when(stockRepository.findAll()).thenReturn(List.of(Stock.builder().symbol("TEST").build()));

        RiskMetricsDto metrics = service.riskMetrics("TEST", 365);

        assertThat(metrics.getMaxDrawdownPct()).isEqualByComparingTo("-25.00");
        assertThat(metrics.getMaxDrawdownTrough()).isEqualTo(LocalDate.of(2025, 1, 5));
        assertThat(metrics.getPeriodReturnPct()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("beta is withheld when too few days overlap the market series")
    void betaWithheldOnThinOverlap() {
        when(priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(any(), any()))
                .thenReturn(alternating("TEST", 30));
        when(priceHistoryRepository.findBySymbolInAndTradeDateGreaterThanEqualOrderBySymbolAscTradeDateAsc(
                anyList(), any())).thenReturn(List.of());
        when(stockRepository.findAll()).thenReturn(List.of(Stock.builder().symbol("TEST").build()));

        RiskMetricsDto metrics = service.riskMetrics("TEST", 365);

        assertThat(metrics.getAnnualisedVolatilityPct()).isNotNull();
        assertThat(metrics.getBeta()).isNull();
        assertThat(metrics.getVarianceExplained()).isNull();
        assertThat(metrics.getMethodology()).anyMatch(line -> line.contains("Beta is omitted"));
    }

    @Test
    @DisplayName("an unknown symbol is a not-found, not an empty result")
    void unknownSymbolRejected() {
        when(stockRepository.findBySymbol(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.riskMetrics("NOPE", 365))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("a symbol perfectly correlates with itself and the matrix is symmetric")
    void correlationIsSymmetricWithUnitDiagonal() {
        when(priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                eq("A"), any())).thenReturn(alternating("A", 60));
        when(priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                eq("B"), any())).thenReturn(alternating("B", 60));

        CorrelationMatrixDto matrix = service.correlationMatrix(List.of("A", "B"), 365);

        assertThat(matrix.getSymbols()).containsExactly("A", "B");
        assertThat(matrix.getMatrix().get(0).get(0)).isEqualByComparingTo("1");
        assertThat(matrix.getMatrix().get(1).get(1)).isEqualByComparingTo("1");
        assertThat(matrix.getMatrix().get(0).get(1))
                .isEqualByComparingTo(matrix.getMatrix().get(1).get(0));
        // Two identical series correlate at exactly 1.
        assertThat(matrix.getMatrix().get(0).get(1)).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("a cell with too little overlap is left null rather than reported as zero")
    void thinOverlapLeavesCellNull() {
        when(priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                eq("A"), any())).thenReturn(alternating("A", 60));
        // B trades on entirely different dates, so nothing aligns.
        List<PriceHistory> disjoint = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            disjoint.add(PriceHistory.builder()
                    .symbol("B")
                    .tradeDate(LocalDate.of(2024, 1, 1).plusDays(i))
                    .close(BigDecimal.valueOf(100 + i))
                    .build());
        }
        when(priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                eq("B"), any())).thenReturn(disjoint);

        CorrelationMatrixDto matrix = service.correlationMatrix(List.of("A", "B"), 365);

        // Zero here would read as "uncorrelated", the most useful answer there
        // is — and we have no evidence for it.
        assertThat(matrix.getMatrix().get(0).get(1)).isNull();
        assertThat(matrix.getOverlap().get(0).get(1)).isZero();
    }

    @Test
    @DisplayName("correlating fewer than two distinct symbols is rejected")
    void needsTwoSymbols() {
        assertThatThrownBy(() -> service.correlationMatrix(List.of("A", "a"), 365))
                .isInstanceOf(InvalidEntityException.class);
    }
}
