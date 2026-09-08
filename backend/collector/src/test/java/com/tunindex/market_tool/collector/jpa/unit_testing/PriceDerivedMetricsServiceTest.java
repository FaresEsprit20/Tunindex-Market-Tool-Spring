package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.fundamentals.PriceDerivedMetricsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Guards the period labels on the two computed metrics.
 *
 * <p>Both of these metrics carry a period in their name. The risk is not that
 * the arithmetic is wrong but that it runs over the wrong window: a return
 * measured across three weeks of history, stored in a field called
 * "one year return", is indistinguishable downstream from a real one. These
 * tests exist to keep the service refusing in that case.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PriceDerivedMetricsService")
class PriceDerivedMetricsServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @InjectMocks
    private PriceDerivedMetricsService service;

    private Stock stock() {
        Stock stock = new Stock();
        stock.setSymbol("TEST");
        return stock;
    }

    /** Daily bars ending today, one per day, at a constant volume. */
    private List<PriceHistory> bars(int days, long volume, String startClose, String endClose) {
        List<PriceHistory> history = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        BigDecimal from = new BigDecimal(startClose);
        BigDecimal to = new BigDecimal(endClose);

        for (int i = 0; i < days; i++) {
            PriceHistory bar = new PriceHistory();
            bar.setSymbol("TEST");
            bar.setTradeDate(start.plusDays(i));
            bar.setVolume(volume);
            // Only the ends matter to the assertions; the middle just has to
            // be present and positive.
            bar.setClose(i == 0 ? from : to);
            history.add(bar);
        }
        return history;
    }

    private void given(List<PriceHistory> history) {
        when(stockRepository.findAll()).thenReturn(List.of(stock()));
        when(priceHistoryRepository
                .findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(anyString(), any()))
                .thenReturn(history);
        when(stockRepository.save(any(Stock.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("computes both metrics when a full year of history is present")
    void computesBothWithFullHistory() {
        given(bars(365, 1000L, "100.00", "125.00"));

        assertThat(service.refreshAll()).isEqualTo(1);
    }

    @Test
    @DisplayName("measures the return over one year, not over all history held")
    void anchorsReturnToTheAnniversary() {
        // 400 days of bars: a 5.00 close on the first day, 10.00 from the
        // second day on. Measured from the oldest bar the return reads +100%;
        // measured from one year back - which is what the field is called - it
        // is 0%, because the price a year ago was already 10.00.
        List<PriceHistory> history = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(399);
        for (int i = 0; i < 400; i++) {
            PriceHistory bar = new PriceHistory();
            bar.setSymbol("TEST");
            bar.setTradeDate(start.plusDays(i));
            bar.setVolume(1000L);
            bar.setClose(i == 0 ? new BigDecimal("5.00") : new BigDecimal("10.00"));
            history.add(bar);
        }
        given(history);

        service.refreshAll();

        Stock result = stockRepository.findAll().get(0);
        assertThat(result.getFundamentalData().getOneYearReturn()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("refuses a one-year return when history spans only a few months")
    void refusesShortHistoryForAnnualReturn() {
        // 120 days of bars is a real dataset - enough to average volume over -
        // but a return across it is a four-month return, not an annual one.
        List<PriceHistory> history = bars(120, 1000L, "100.00", "125.00");
        given(history);

        service.refreshAll();

        Stock result = stockRepository.findAll().get(0);
        assertThat(result.getFundamentalData()).isNull();
    }

    @Test
    @DisplayName("still averages volume over a short-but-adequate window")
    void averagesVolumeOnShortHistory() {
        given(bars(120, 4200L, "100.00", "125.00"));

        service.refreshAll();

        Stock result = stockRepository.findAll().get(0);
        assertThat(result.getVolumeData()).isNotNull();
        assertThat(result.getVolumeData().getAvgVolume3m()).isEqualTo(4200L);
    }

    @Test
    @DisplayName("refuses to average volume from too few observations")
    void refusesSparseVolume() {
        // Ten prints in a quarter is a stock that barely trades; an average
        // over them would read as a liquidity figure it cannot support.
        given(bars(10, 1000L, "100.00", "125.00"));

        service.refreshAll();

        Stock result = stockRepository.findAll().get(0);
        assertThat(result.getVolumeData()).isNull();
    }

    @Test
    @DisplayName("skips a symbol with no stored history rather than failing")
    void toleratesEmptyHistory() {
        given(List.of());

        assertThat(service.refreshAll()).isZero();
    }
}
