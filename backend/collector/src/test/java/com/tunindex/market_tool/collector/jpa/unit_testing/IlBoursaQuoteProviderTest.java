package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaQuoteProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parser tests for the exchange quote page.
 *
 * <p>These exist because of a live data-integrity failure: the price cell is
 * rendered as "91,15" for some symbols and "84,68&nbsp;TND" for others on the
 * same template. The parser assumed the former, threw on the latter, and
 * returned null — and the caller silently kept the fundamentals provider's
 * price, which lags a full trading day. Two thirds of the exchange went stale
 * that way, and a delisted name reached the top of the gainers list.
 */
@DisplayName("IlBoursaQuoteProvider — quote page parsing")
class IlBoursaQuoteProviderTest {

    private final IlBoursaQuoteProvider provider = new IlBoursaQuoteProvider(null);

    /** Mirrors the real page: OUVERTURE/+HAUT left, CLOTURE VEILLE/+BAS right. */
    private String page(String price, String open, String high, String prevClose, String low, String volume) {
        return "<html><body>"
                + "<div class=\"cot_v1\"><div class=\"cot_v1a\">COURS</div>"
                + "<div class=\"cot_v1b\">" + price + "</div></div>"
                + "<div class=\"cot_v2 clearfix\">"
                + "<div class=\"cot_v21\"><div>OUVERTURE</div><div>" + open + "</div>"
                + "<div>+ HAUT</div><div>" + high + "</div></div>"
                + "<div class=\"cot_v22\"><div><span>CLOTURE </span>VEILLE</div><div>" + prevClose + "</div>"
                + "<div>+ BAS</div><div>" + low + "</div></div>"
                + "</div>"
                + "<div id=\"vol\">" + volume + "</div>"
                + "</body></html>";
    }

    private IlBoursaQuoteProvider.LiveQuote parse(String html) {
        return ReflectionTestUtils.invokeMethod(provider, "parse", "TEST", html);
    }

    @Test
    @DisplayName("reads a plain price cell")
    void plainPrice() {
        IlBoursaQuoteProvider.LiveQuote quote =
                parse(page("91,15", "91,15", "92,00", "92,00", "91,15", "9 658"));

        assertThat(quote.lastPrice()).isEqualByComparingTo("91.15");
        assertThat(quote.open()).isEqualByComparingTo("91.15");
        assertThat(quote.dayHigh()).isEqualByComparingTo("92.00");
        assertThat(quote.prevClose()).isEqualByComparingTo("92.00");
        assertThat(quote.dayLow()).isEqualByComparingTo("91.15");
        assertThat(quote.volume()).isEqualTo(9658L);
    }

    @Test
    @DisplayName("reads a price cell that also carries the currency")
    void priceWithCurrencySuffix() {
        // The exact shape that broke production.
        IlBoursaQuoteProvider.LiveQuote quote =
                parse(page("84,68 TND", "84,00 TND", "85,10 TND",
                        "86,40 TND", "84,00 TND", "1 234"));

        assertThat(quote.lastPrice()).isEqualByComparingTo("84.68");
        assertThat(quote.prevClose()).isEqualByComparingTo("86.40");
        assertThat(quote.dayHigh()).isEqualByComparingTo("85.10");
        assertThat(quote.volume()).isEqualTo(1234L);
    }

    @Test
    @DisplayName("pairs each label with its own value, so prevClose is never the day low")
    void fieldsAreNotTransposed() {
        IlBoursaQuoteProvider.LiveQuote quote =
                parse(page("10,00", "11,00", "12,00", "13,00", "14,00", "5"));

        // Positional parsing is only safe while this ordering holds; if the
        // page ever reorders these blocks, this test is what catches it.
        assertThat(quote.open()).isEqualByComparingTo("11.00");
        assertThat(quote.dayHigh()).isEqualByComparingTo("12.00");
        assertThat(quote.prevClose()).isEqualByComparingTo("13.00");
        assertThat(quote.dayLow()).isEqualByComparingTo("14.00");
    }

    @Test
    @DisplayName("a cell with no number yields null rather than a wrong figure")
    void nonNumericCellIsNull() {
        IlBoursaQuoteProvider.LiveQuote quote =
                parse(page("--", "n/d", "-", "-", "-", "-"));

        assertThat(quote.lastPrice()).isNull();
        assertThat(quote.prevClose()).isNull();
        assertThat(quote.volume()).isNull();
    }

    @Test
    @DisplayName("a page missing the quote blocks entirely yields nulls, not an exception")
    void missingMarkup() {
        IlBoursaQuoteProvider.LiveQuote quote = parse("<html><body>nothing here</body></html>");

        assertThat(quote.lastPrice()).isNull();
        assertThat(quote.open()).isNull();
        assertThat(quote.volume()).isNull();
    }

    @Test
    @DisplayName("a negative value keeps its sign")
    void negativeValue() {
        assertThat(parse(page("-1,50", "0", "0", "0", "0", "0")).lastPrice())
                .isEqualByComparingTo("-1.50");
    }

    @Test
    @DisplayName("thousands separated by a non-breaking space parse as one volume")
    void groupedVolume() {
        // "1 234 567" must not become 1.
        assertThat(parse(page("1,00", "1", "1", "1", "1", "1 234 567")).volume())
                .isEqualTo(1234567L);
    }
}
