package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.services.scoring.TunindexScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shape of the blend, and the rule that stops it contradicting the
 * analyst.
 *
 * <p>Weights are the kind of thing that get nudged in passing, and a nudge
 * here silently re-ranks every stock on the site with nothing to show for it
 * in a diff of behaviour. So the intended split is written down: not because
 * these exact numbers are sacred, but because changing them should be a
 * decision somebody made on purpose rather than a side effect.
 */
@DisplayName("TunindexScorer weighting")
class TunindexScorerWeightingTest {

    private int weight(String field) {
        Object value = ReflectionTestUtils.getField(TunindexScorer.class, field);
        return value == null ? -1 : (int) value;
    }

    @Test
    @DisplayName("the weights still add up to 100")
    void weightsSumToOneHundred() {
        // The blend divides by the sum of the weights that had data, so an
        // arithmetic slip here does not throw - it quietly rescales every
        // score on the site.
        int total = weight("WEIGHT_VALUATION")
                + weight("WEIGHT_TIMING")
                + weight("WEIGHT_FINANCIAL_HEALTH")
                + weight("WEIGHT_INCOME")
                + weight("WEIGHT_MOMENTUM")
                + weight("WEIGHT_NEWS");

        assertThat(total).isEqualTo(100);
    }

    @Test
    @DisplayName("the technical read carries 40% of the score")
    void technicalsCarryFortyPercent() {
        // Timing plus momentum is the technical half of the blend. It was 35%
        // and was raised deliberately: a cheap, healthy company whose chart is
        // still falling should not rank as a buy, which is what the old split
        // allowed.
        int technical = weight("WEIGHT_TIMING") + weight("WEIGHT_MOMENTUM");

        assertThat(technical).isEqualTo(40);
    }

    @Test
    @DisplayName("valuation and timing carry equal weight")
    void valuationAndTimingAreEqual() {
        // The tie is the design, not a rounding accident: what you buy and
        // when you buy it matter the same amount. Tipping it either way turns
        // this into a different tool - valuation-dominant brings back the
        // "cheap but still falling" ranking, timing-dominant makes it a
        // momentum tool wearing a value tool's clothes.
        assertThat(weight("WEIGHT_TIMING")).isEqualTo(weight("WEIGHT_VALUATION"));
    }

    @Test
    @DisplayName("valuation and timing each outweigh everything else")
    void thePairLeads() {
        int floor = Math.max(Math.max(weight("WEIGHT_FINANCIAL_HEALTH"), weight("WEIGHT_INCOME")),
                Math.max(weight("WEIGHT_MOMENTUM"), weight("WEIGHT_NEWS")));

        assertThat(weight("WEIGHT_VALUATION")).isGreaterThan(floor);
        assertThat(weight("WEIGHT_TIMING")).isGreaterThan(floor);
    }
}
