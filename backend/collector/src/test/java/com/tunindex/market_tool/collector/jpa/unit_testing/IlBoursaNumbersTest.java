package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaNumbers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the number conventions, because the site mixes two of them.
 *
 * <p>The company page writes "0,75" and "547 525"; the cote table writes
 * "218.26" and "9 422". Reading one with the other's rule does not throw - it
 * silently yields a value off by orders of magnitude, which is the kind of
 * error that reaches a valuation before anyone notices.
 */
@DisplayName("IlBoursaNumbers")
class IlBoursaNumbersTest {

    @Test
    @DisplayName("reads French decimals from the company page")
    void parsesCommaDecimals() {
        assertThat(IlBoursaNumbers.parse("0,75")).isEqualByComparingTo("0.75");
        assertThat(IlBoursaNumbers.parse("11,05")).isEqualByComparingTo("11.05");
        assertThat(IlBoursaNumbers.parse("-0,34")).isEqualByComparingTo("-0.34");
    }

    @Test
    @DisplayName("reads dot decimals from the cote table")
    void parsesDotDecimals() {
        assertThat(IlBoursaNumbers.parse("218.26")).isEqualByComparingTo("218.26");
        assertThat(IlBoursaNumbers.parse("0.41")).isEqualByComparingTo("0.41");
    }

    @Test
    @DisplayName("strips spaced thousands in both styles")
    void parsesSpacedThousands() {
        // Ordinary space, non-breaking space, and narrow no-break space all
        // appear on these pages.
        assertThat(IlBoursaNumbers.parse("547 525")).isEqualByComparingTo("547525");
        assertThat(IlBoursaNumbers.parse("9 422")).isEqualByComparingTo("9422");
        assertThat(IlBoursaNumbers.parse("3 589 096")).isEqualByComparingTo("3589096");
        assertThat(IlBoursaNumbers.parse("1 234,56")).isEqualByComparingTo("1234.56");
    }

    @Test
    @DisplayName("drops percent signs and currency suffixes")
    void stripsUnits() {
        assertThat(IlBoursaNumbers.parse("59,92%")).isEqualByComparingTo("59.92");
        assertThat(IlBoursaNumbers.parse("-0.55%")).isEqualByComparingTo("-0.55");
        assertThat(IlBoursaNumbers.parse("8,30 TND")).isEqualByComparingTo("8.30");
    }

    @Test
    @DisplayName("treats an unreported figure as absent, never as zero")
    void unreportedIsNull() {
        // A company that did not publish a margin has not published a margin
        // of zero; the scorer drops one and penalises the other.
        assertThat(IlBoursaNumbers.parse("-")).isNull();
        assertThat(IlBoursaNumbers.parse("--")).isNull();
        assertThat(IlBoursaNumbers.parse("n/a")).isNull();
        assertThat(IlBoursaNumbers.parse("")).isNull();
        assertThat(IlBoursaNumbers.parse(null)).isNull();
    }

    @Test
    @DisplayName("refuses text that is not purely a number")
    void refusesNonNumbers() {
        // The shares-outstanding label sits inline with the free float, and a
        // looser parser once turned "270 000 000 Flottant : 50,00%" into a
        // single unparseable run rather than declining.
        assertThat(IlBoursaNumbers.parse("270 000 000 Flottant : 50,00%")).isNull();
        assertThat(IlBoursaNumbers.parse("Chiffre d'affaires")).isNull();
    }

    @Test
    @DisplayName("reads a leading plus sign as a positive change")
    void parsesSignedChange() {
        assertThat(IlBoursaNumbers.parse("+1,59%")).isEqualByComparingTo("1.59");
    }

    @Test
    @DisplayName("parseLong truncates to whole units for counts")
    void parsesCounts() {
        assertThat(IlBoursaNumbers.parseLong("270 000 000")).isEqualTo(270_000_000L);
        assertThat(IlBoursaNumbers.parseLong("0")).isZero();
        assertThat(IlBoursaNumbers.parseLong("-")).isNull();
    }
}
