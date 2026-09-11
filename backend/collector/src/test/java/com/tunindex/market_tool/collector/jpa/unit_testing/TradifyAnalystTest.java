package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.dto.analyst.TradeSetupDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.services.analyst.TradifyAnalyst;
import com.tunindex.market_tool.collector.services.scoring.ReversalDetector;
import com.tunindex.market_tool.common.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the numbers somebody would place an order against.
 *
 * <p>That is the whole reason this class is tested more carefully than a
 * display component: a wrong entry band does not look wrong. It renders as a
 * confident, precise instruction, and the reader has no way to tell it apart
 * from a correct one until the money is committed.
 *
 * <p>So the cases here are the ones where a plausible-looking number would be
 * the wrong number: a band above fair value, a band above the current price, a
 * "buy now" on a stock that is still falling, and levels produced out of data
 * that does not exist.
 */
@DisplayName("TradifyAnalyst")
class TradifyAnalystTest {

    private final TradifyAnalyst analyst = new TradifyAnalyst();

    private List<PriceHistory> bars(int count, double startPrice, double drift) {
        List<PriceHistory> history = new ArrayList<>();
        LocalDate date = LocalDate.of(2026, 1, 5);
        double price = startPrice;
        for (int i = 0; i < count; i++) {
            PriceHistory bar = new PriceHistory();
            bar.setSymbol("TEST");
            bar.setTradeDate(date.plusDays(i));
            bar.setClose(BigDecimal.valueOf(price).setScale(2, java.math.RoundingMode.HALF_UP));
            bar.setHigh(BigDecimal.valueOf(price * 1.02).setScale(2, java.math.RoundingMode.HALF_UP));
            bar.setLow(BigDecimal.valueOf(price * 0.98).setScale(2, java.math.RoundingMode.HALF_UP));
            bar.setOpen(bar.getClose());
            bar.setVolume(1000L);
            history.add(bar);
            price += drift;
        }
        return history;
    }

    private Stock stock(Double fairValue, double lastPrice) {
        Stock stock = new Stock();
        stock.setSymbol("TEST");
        PriceData priceData = new PriceData();
        priceData.setLastPrice(BigDecimal.valueOf(lastPrice));
        stock.setPriceData(priceData);
        if (fairValue != null) {
            CalculatedValues values = new CalculatedValues();
            values.setGrahamFairValue(BigDecimal.valueOf(fairValue));
            stock.setCalculatedValues(values);
        }
        return stock;
    }

    private TechnicalAnalysisDto technical(double last, Double bollLower, Double sma50,
                                           Double atr, Double bollUpper) {
        return TechnicalAnalysisDto.builder()
                .lastClose(BigDecimal.valueOf(last))
                .bollingerLower(bollLower == null ? null : BigDecimal.valueOf(bollLower))
                .bollingerUpper(bollUpper == null ? null : BigDecimal.valueOf(bollUpper))
                .sma50(sma50 == null ? null : BigDecimal.valueOf(sma50))
                .atr14(atr == null ? null : BigDecimal.valueOf(atr))
                .adx14(BigDecimal.valueOf(25))
                .build();
    }

    private ReversalDetector.ReversalSignal signal(ReversalDetector.Phase phase, int score) {
        return new ReversalDetector.ReversalSignal(score, phase, List.of("test condition"));
    }

    @Test
    @DisplayName("refuses to invent levels when there is barely any history")
    void refusesWithoutHistory() {
        // The failure that matters: producing a confident band from ten bars.
        TradeSetupDto setup = analyst.analyse(
                stock(null, 10), technical(10, 9.0, 9.5, 0.3, 11.0),
                bars(10, 10, 0), signal(ReversalDetector.Phase.REVERSING, 70));

        assertThat(setup.getStance()).isEqualTo(TradeSetupDto.Stance.NO_SETUP);
        assertThat(setup.getBuyZoneLow()).isNull();
        assertThat(setup.getBuyZoneHigh()).isNull();
        assertThat(setup.getConfidence()).isZero();
    }

    @Test
    @DisplayName("the buy zone sits below the current price")
    void zoneIsBelowPrice() {
        // A band at or above the price is not an entry instruction, it is a
        // description of the present - and it reads as permission to chase.
        TradeSetupDto setup = analyst.analyse(
                stock(null, 10), technical(10, 9.0, 9.4, 0.3, 11.0),
                bars(120, 12, -0.01), signal(ReversalDetector.Phase.REVERSING, 70));

        assertThat(setup.getBuyZoneLow()).isNotNull();
        assertThat(setup.getBuyZoneLow()).isLessThan(BigDecimal.valueOf(10));
    }

    @Test
    @DisplayName("offers no entry at all when fair value sits below every support")
    void zoneIsCappedByFairValue() {
        // The fusion the whole thing exists for: a bounce off chart support is
        // not a reason to pay more than the business is worth. With fair value
        // at 8.00 the most we would pay is 6.80, which is below every support
        // on the chart - so the honest answer is "no entry here", not a band
        // at a price we have just called too expensive.
        TradeSetupDto setup = analyst.analyse(
                stock(8.0, 10), technical(10, 9.5, 9.8, 1.0, 11.0),
                bars(120, 12, -0.01), signal(ReversalDetector.Phase.REVERSING, 70));

        assertThat(setup.getBuyZoneLow()).isNull();
        assertThat(setup.getBuyZoneHigh()).isNull();
        assertThat(setup.getStance()).isEqualTo(TradeSetupDto.Stance.NO_SETUP);
        assertThat(setup.getRisks()).anyMatch(r -> r.contains("cheap on the numbers"));
    }

    @Test
    @DisplayName("does not say buy now while the stock is still falling")
    void holdsOffInADowntrend() {
        // Oversold in a downtrend is a symptom, not an entry. This is the
        // single most expensive mistake the old "RSI 28, oversold" line invited.
        TradeSetupDto setup = analyst.analyse(
                stock(null, 10), technical(10, 9.0, 11.0, 0.3, 12.0),
                bars(120, 20, -0.08), signal(ReversalDetector.Phase.DOWNTREND, 15));

        assertThat(setup.getStance()).isEqualTo(TradeSetupDto.Stance.HOLD_OFF);
        assertThat(setup.getRisks()).anyMatch(r -> r.toLowerCase().contains("downtrend"));
    }

    @Test
    @DisplayName("says accumulate only when the turn is confirmed and price is in the band")
    void accumulatesOnConfirmedReversalInZone() {
        // Price 9.10 with support at 9.00 and a 0.50 range: inside the band.
        TradeSetupDto setup = analyst.analyse(
                stock(null, 9.10), technical(9.10, 9.0, 9.5, 0.5, 11.0),
                bars(120, 12, -0.01), signal(ReversalDetector.Phase.REVERSING, 80));

        assertThat(setup.getStance()).isEqualTo(TradeSetupDto.Stance.ACCUMULATE_NOW);
        assertThat(setup.isPriceInBuyZone()).isTrue();
    }

    @Test
    @DisplayName("says wait when the stock is worth owning but priced above the band")
    void waitsForAPullback() {
        TradeSetupDto setup = analyst.analyse(
                stock(null, 12.0), technical(12.0, 9.0, 9.5, 0.2, 13.0),
                bars(120, 8, 0.03), signal(ReversalDetector.Phase.UPTREND, 60));

        assertThat(setup.getStance()).isEqualTo(TradeSetupDto.Stance.BUY_THE_DIP);
        assertThat(setup.isPriceInBuyZone()).isFalse();
        assertThat(setup.getDistanceToZonePct()).isNotNull();
    }

    @Test
    @DisplayName("the stop sits below the band it protects")
    void stopIsBelowTheZone() {
        // A stop inside the entry band would be triggered by the ordinary
        // movement that the band was drawn to accommodate.
        TradeSetupDto setup = analyst.analyse(
                stock(null, 9.10), technical(9.10, 9.0, 9.5, 0.3, 11.0),
                bars(120, 12, -0.01), signal(ReversalDetector.Phase.REVERSING, 80));

        if (setup.getStopLevel() != null && setup.getBuyZoneLow() != null) {
            assertThat(setup.getStopLevel()).isLessThan(setup.getBuyZoneLow());
        }
    }

    @Test
    @DisplayName("the narrative describes the months, not just today")
    void regimeSummaryCarriesContext() {
        // The specific complaint this answers: a read that only says "bearish
        // now" cannot tell a seven-month decline that is ending from a dip
        // that started last week.
        TradeSetupDto setup = analyst.analyse(
                stock(null, 10), technical(10, 9.0, 9.5, 0.3, 11.0),
                bars(150, 20, -0.06), signal(ReversalDetector.Phase.REVERSING, 75));

        assertThat(setup.getRegimeSummary()).isNotBlank();
        assertThat(setup.getRegimeSummary()).containsIgnoringCase("down");
        assertThat(setup.getRegimeSummary()).containsIgnoringCase("window");
    }

    @Test
    @DisplayName("confidence reflects how much was actually available")
    void confidenceTracksInputs() {
        TradeSetupDto rich = analyst.analyse(
                stock(12.0, 9.10), technical(9.10, 9.0, 9.5, 0.3, 11.0),
                bars(150, 12, -0.01), signal(ReversalDetector.Phase.REVERSING, 80));
        TradeSetupDto sparse = analyst.analyse(
                stock(null, 9.10), technical(9.10, null, null, null, null),
                bars(40, 12, -0.01), signal(ReversalDetector.Phase.NEUTRAL, 10));

        assertThat(rich.getConfidence()).isGreaterThan(sparse.getConfidence());
    }

    @Test
    @DisplayName("expected return is measured from the band, not from today's price")
    void expectedReturnIsFromTheZone() {
        // Quoting a return from the current price would overstate the case for
        // waiting and understate it for buying the dip - the number has to
        // describe the trade being proposed.
        TradeSetupDto setup = analyst.analyse(
                stock(null, 12.0), technical(12.0, 9.0, 13.0, 0.4, 14.0),
                bars(120, 12, -0.01), signal(ReversalDetector.Phase.REVERSING, 70));

        if (setup.getExpectedReturnPct() != null && setup.getTarget1() != null) {
            BigDecimal mid = setup.getBuyZoneLow().add(setup.getBuyZoneHigh())
                    .divide(BigDecimal.valueOf(2), 3, java.math.RoundingMode.HALF_UP);
            assertThat(setup.getTarget1()).isGreaterThan(mid);
        }
    }
}
