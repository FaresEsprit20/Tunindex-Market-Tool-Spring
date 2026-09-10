package com.tunindex.market_tool.ads;

import com.tunindex.market_tool.ads.entities.AdEvent;
import com.tunindex.market_tool.ads.entities.Advertisement;
import com.tunindex.market_tool.ads.entities.enums.AdSource;
import com.tunindex.market_tool.ads.entities.enums.PricingModel;
import com.tunindex.market_tool.ads.service.AdRevenueCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what each event is worth.
 *
 * <p>This is the arithmetic that turns traffic into an invoice, so the
 * failures worth guarding against are the quiet ones: billing an impression
 * under a per-click deal, paying for a video nobody finished, or counting a
 * house ad as income. None of those would throw - they would just produce a
 * number somebody acts on.
 */
@DisplayName("AdRevenueCalculator")
class AdRevenueCalculatorTest {

    private final AdRevenueCalculator calculator = new AdRevenueCalculator();

    private Advertisement ad(AdSource source, PricingModel model, String rate) {
        return Advertisement.builder()
                .source(source)
                .pricingModel(model)
                .rate(rate == null ? null : new BigDecimal(rate))
                .currency("TND")
                .build();
    }

    @Test
    @DisplayName("CPM pays per thousand impressions, not per impression")
    void cpmDividesByThousand() {
        // 5.00 per mille is 0.005 for one impression. Treating the rate as
        // per-impression would overstate revenue by a factor of a thousand.
        Advertisement ad = ad(AdSource.GOOGLE_ADSENSE, PricingModel.CPM, "5.00");

        assertThat(calculator.revenueFor(ad, AdEvent.EventType.IMPRESSION))
                .isEqualByComparingTo("0.005");
    }

    @Test
    @DisplayName("keeps enough precision that a single impression is not rounded away")
    void keepsSubMillimePrecision() {
        // At two decimal places this would be 0.00 and a million impressions
        // would report as earning nothing.
        Advertisement ad = ad(AdSource.GOOGLE_ADSENSE, PricingModel.CPM, "1.00");

        assertThat(calculator.revenueFor(ad, AdEvent.EventType.IMPRESSION).signum())
                .isPositive();
    }

    @Test
    @DisplayName("a CPC deal pays for clicks and nothing for impressions")
    void cpcPaysOnlyForClicks() {
        Advertisement ad = ad(AdSource.DIRECT_ADVERTISER, PricingModel.CPC, "0.40");

        assertThat(calculator.revenueFor(ad, AdEvent.EventType.CLICK))
                .isEqualByComparingTo("0.40");
        assertThat(calculator.revenueFor(ad, AdEvent.EventType.IMPRESSION))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a CPV deal pays for completed views, not for abandoned ones")
    void cpvRequiresCompletion() {
        Advertisement ad = ad(AdSource.YOUTUBE, PricingModel.CPV, "0.02");

        assertThat(calculator.revenueFor(ad, AdEvent.EventType.COMPLETED_VIEW))
                .isEqualByComparingTo("0.02");
        // The distinction the whole model rests on: a video that started and
        // was abandoned is exactly what cost-per-view does not buy.
        assertThat(calculator.revenueFor(ad, AdEvent.EventType.SKIPPED))
                .isEqualByComparingTo("0");
        assertThat(calculator.revenueFor(ad, AdEvent.EventType.IMPRESSION))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a house ad earns nothing however much it is shown")
    void houseAdsEarnNothing() {
        // Promoting ourselves is not income. Counting it would inflate every
        // total with money nobody is paying.
        Advertisement ad = ad(AdSource.HOUSE, PricingModel.CPM, "5.00");

        assertThat(calculator.revenueFor(ad, AdEvent.EventType.IMPRESSION))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("flat-rate and network-reported deals accrue nothing per event")
    void nonEventDrivenModelsAccrueNothing() {
        // Both are settled outside this module; accruing here as well would
        // bill the same money twice.
        assertThat(calculator.revenueFor(
                ad(AdSource.DIRECT_ADVERTISER, PricingModel.FLAT_RATE, "500"),
                AdEvent.EventType.IMPRESSION)).isEqualByComparingTo("0");

        assertThat(calculator.revenueFor(
                ad(AdSource.GOOGLE_ADSENSE, PricingModel.NETWORK_REPORTED, "5"),
                AdEvent.EventType.IMPRESSION)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("returns zero rather than null on incomplete input")
    void toleratesMissingFields() {
        // Callers sum these; a null here becomes a crash on the first
        // half-configured ad rather than a visible zero.
        assertThat(calculator.revenueFor(null, AdEvent.EventType.CLICK)).isEqualByComparingTo("0");
        assertThat(calculator.revenueFor(ad(AdSource.DIRECT_ADVERTISER, PricingModel.CPC, null),
                AdEvent.EventType.CLICK)).isEqualByComparingTo("0");
        assertThat(calculator.revenueFor(ad(AdSource.DIRECT_ADVERTISER, PricingModel.CPC, "0"),
                AdEvent.EventType.CLICK)).isEqualByComparingTo("0");
        assertThat(calculator.revenueFor(ad(AdSource.DIRECT_ADVERTISER, PricingModel.CPC, "1"), null))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a conversion pays under CPA and not under CPC")
    void cpaPaysOnConversion() {
        Advertisement ad = ad(AdSource.AFFILIATE, PricingModel.CPA, "12.50");

        assertThat(calculator.revenueFor(ad, AdEvent.EventType.CONVERSION))
                .isEqualByComparingTo("12.50");
        assertThat(calculator.revenueFor(ad, AdEvent.EventType.CLICK))
                .isEqualByComparingTo("0");
    }
}
