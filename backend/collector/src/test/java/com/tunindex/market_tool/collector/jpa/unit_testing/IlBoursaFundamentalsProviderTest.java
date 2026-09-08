package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaFundamentalsProvider;
import com.tunindex.market_tool.collector.services.scraping.PageFetcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Covers the two ways a second source can make the data worse.
 *
 * <p>A French-formatted page read with an English number parser yields
 * nothing, which is merely useless. Reading the rightmost column without
 * checking its year is the dangerous one: a company that stopped filing keeps
 * its last results on the page forever, so the newest column can be years old
 * and still look current. Filing that as this year's EPS is worse than the
 * blank it replaced, because nothing downstream can tell it is stale.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IlBoursaFundamentalsProvider")
class IlBoursaFundamentalsProviderTest {

    @Mock
    private PageFetcher pageFetcher;

    @InjectMocks
    private IlBoursaFundamentalsProvider provider;

    /** The real page shape, with non-breaking spaces and comma decimals. */
    private String page(int latestYear, String eps, String per) {
        int y = latestYear;
        return "<html><body>"
                + "<p>Nombre de titres : 270 000 000 Flottant : 50,00%</p>"
                + "<table>"
                + "<tr><td></td><td>" + (y - 4) + "</td><td>" + (y - 3) + "</td><td>" + (y - 2)
                + "</td><td>" + (y - 1) + "</td><td>" + y + "</td></tr>"
                + "<tr><td>Chiffre d'affaires</td><td>385 983</td><td>428 815</td>"
                + "<td>493 636</td><td>518 336</td><td>547 525</td></tr>"
                + "<tr><td>Résultat net</td><td>152 124</td><td>168 562</td>"
                + "<td>182 322</td><td>199 437</td><td>202 819</td></tr>"
                + "<tr><td>BNPA</td><td>0,68</td><td>0,62</td><td>0,68</td><td>0,74</td>"
                + "<td>" + eps + "</td></tr>"
                + "<tr><td>PER</td><td>12,28</td><td>13,30</td><td>12,30</td><td>11,23</td>"
                + "<td>" + per + "</td></tr>"
                + "<tr><td>Dividende</td><td>0,28</td><td>0,28</td><td>0,29</td><td>0,35</td>"
                + "<td>0,35</td></tr>"
                + "</table></body></html>";
    }

    @Test
    @DisplayName("reads French-formatted figures from the latest fiscal year")
    void parsesCurrentYear() {
        int thisYear = Year.now().getValue();
        when(pageFetcher.fetch(anyString())).thenReturn(page(thisYear, "0,75", "11,05"));

        var result = provider.fetch("BT");

        assertThat(result).isNotNull();
        assertThat(result.fiscalYear()).isEqualTo(thisYear);
        assertThat(result.eps()).isEqualByComparingTo("0.75");
        assertThat(result.peRatio()).isEqualByComparingTo("11.05");
        assertThat(result.dividendPerShare()).isEqualByComparingTo("0.35");
        // Printed in thousands of dinars on the page.
        assertThat(result.revenue()).isEqualByComparingTo("547525000");
        assertThat(result.netIncome()).isEqualByComparingTo("202819000");
        assertThat(result.sharesOutstanding()).isEqualTo(270_000_000L);
    }

    @Test
    @DisplayName("computes profit margin from the two figures it read")
    void computesProfitMargin() {
        when(pageFetcher.fetch(anyString()))
                .thenReturn(page(Year.now().getValue(), "0,75", "11,05"));

        var result = provider.fetch("BT");

        // 202 819 / 547 525 = 37.04%
        assertThat(result.profitMargin()).isEqualByComparingTo("37.04");
    }

    @Test
    @DisplayName("refuses a page whose newest results are years old")
    void refusesStaleFilings() {
        // ADWYA's real situation: the company stopped publishing, so the page
        // still shows its last filing as though it were current.
        when(pageFetcher.fetch(anyString()))
                .thenReturn(page(Year.now().getValue() - 4, "-0,34", "-"));

        assertThat(provider.fetch("ADWYA")).isNull();
    }

    @Test
    @DisplayName("treats a dash as not reported rather than as a number")
    void handlesUnreportedValues() {
        when(pageFetcher.fetch(anyString()))
                .thenReturn(page(Year.now().getValue(), "-0,34", "-"));

        var result = provider.fetch("LOSS");

        assertThat(result).isNotNull();
        // A loss-making company: EPS is negative and there is no P/E to give.
        assertThat(result.eps()).isEqualByComparingTo("-0.34");
        assertThat(result.peRatio()).isNull();
    }

    @Test
    @DisplayName("returns nothing when the page has no year header")
    void toleratesUnexpectedMarkup() {
        when(pageFetcher.fetch(anyString()))
                .thenReturn("<html><body><p>Page introuvable</p></body></html>");

        assertThat(provider.fetch("NOPE")).isNull();
    }

    @Test
    @DisplayName("returns nothing when the fetch yields an empty page")
    void toleratesEmptyFetch() {
        when(pageFetcher.fetch(anyString())).thenReturn("");

        assertThat(provider.fetch("BT")).isNull();
    }
}
