package com.tunindex.market_tool.collector.jpa.unit_testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bulletin line format against lines taken verbatim from the real
 * PDF for the session of 8 September 2026.
 *
 * <p>The exchange's own bulletin is the record every other source republishes,
 * so its parsing is worth holding still. The layout is awkward on purpose: the
 * PDF's header spans several lines and the extractor interleaves them, so
 * columns cannot be counted from the header. Worse, the securities do not
 * share one layout - a continuously traded share carries three prices before
 * the ISIN, a share quoted at a fixing carries one and ends at the dividend
 * date with no last price at all, and some lines put a state flag against the
 * ISIN. Assuming the first layout parsed 57 of 73 securities and silently
 * dropped the rest.
 *
 * <p>The ISIN and the mnemonic are the only unambiguous anchors, which is why
 * the pattern is built around them and why the numbers ahead of the ISIN are
 * only interpreted when there are exactly three of them.
 */
@DisplayName("BVMT bulletin line format")
class BvmtBulletinProviderTest {

    /** Kept identical to the provider's pattern. */
    private static final Pattern QUOTE_LINE = Pattern.compile(
            "^(?<name>[^0-9]{2,60}?)\\s+"
                    + "(?<head>[\\d\\s.,]+?)\\s*[A-Z]?"
                    + "(?<isin>TN[0-9A-Z]{10})\\s+"
                    + "(?<close>[\\d\\s.,]+?)"
                    + "(?<mnemo>[A-Z][A-Z0-9]{1,7})\\s+"
                    + "(?<nominal>[\\d\\s.,]+?)\\s+"
                    + "(?<dividend>[\\d\\s.,]+?)\\s+"
                    + "(?<divdate>\\d{2}/\\d{2}/\\d{4})\\s*"
                    + "(?<last>[\\d\\s.,]*?)\\s*"
                    + "Groupe");

    /** Lines copied from the 08/09/2026 bulletin, unedited. */
    private static final String AMEN_BANK =
            "AMEN BANK 91,000 91,190 89,500TN0003400058 90,500AB 5,000 3,600 29/05/2026 "
                    + "90,500Groupe 11 - Continu 91,0001";
    private static final String BIAT =
            "BIAT 161,500 161,700 160,000TN0001800457 161,500BIAT 5,000 6,000 12/05/2026 "
                    + "161,500Groupe 11 - Continu 161,5007";
    private static final String ATB =
            "ATB 3,600 3,700 3,550TN0003600350 3,550ATB 1,000 0,070 09/05/2019 3,550"
                    + "Groupe 11 - Continu 3,7004";
    /** A fixing: one price, a state flag, and no last price before "Groupe". */
    private static final String SIPHAT =
            "SIPHAT 4,970TN0006670012 4,970SIPHA 5,000 0,200 18/07/2011 Groupe 99 - Fixing69";
    private static final String UADH =
            "UADH 0,430 STN0007690019 0,430UADH 1,000 0,150 28/12/2018 Groupe 99 - Fixing75";
    /** Four numbers ahead of the ISIN, with the reference price leading. */
    private static final String SOTUMAG =
            "SOTUMAG 22,260 23,000 23,590 23,000 HTN0006580013 23,590MGR 1,000 0,580 "
                    + "08/07/2026 23,590Groupe 11 - Continu48";

    private Map<String, String> parse(String line) {
        Matcher m = QUOTE_LINE.matcher(line);
        if (!m.find()) {
            return Map.of();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String g : new String[]{"name", "head", "isin", "close", "mnemo",
                "nominal", "dividend", "divdate", "last"}) {
            fields.put(g, m.group(g) == null ? "" : m.group(g));
        }
        return fields;
    }

    private BigDecimal number(String raw) {
        return new BigDecimal(raw.replace(" ", "").replace(",", "."));
    }

    private LocalDate date(String raw) {
        return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    @Test
    @DisplayName("reads ticker, ISIN and price off a continuously traded line")
    void parsesContinuousQuote() {
        Map<String, String> f = parse(AMEN_BANK);

        assertThat(f).isNotEmpty();
        assertThat(f.get("mnemo")).isEqualTo("AB");
        assertThat(f.get("isin")).isEqualTo("TN0003400058");
        assertThat(f.get("name").trim()).isEqualTo("AMEN BANK");
        // The value that must never drift: this is the price the app shows,
        // and it matches both the live venue and our stored quote.
        assertThat(number(f.get("close"))).isEqualByComparingTo("90.500");
        assertThat(number(f.get("last"))).isEqualByComparingTo("90.500");
    }

    @Test
    @DisplayName("separates a mnemonic that repeats the company name")
    void parsesBiat() {
        // "BIAT" is both the name and the mnemonic - the case a name-based
        // split gets wrong.
        Map<String, String> f = parse(BIAT);

        assertThat(f.get("mnemo")).isEqualTo("BIAT");
        assertThat(number(f.get("close"))).isEqualByComparingTo("161.500");
    }

    @Test
    @DisplayName("reads a fixing line, which carries no last price")
    void parsesFixingQuote() {
        Map<String, String> f = parse(SIPHAT);

        assertThat(f).isNotEmpty();
        assertThat(f.get("mnemo")).isEqualTo("SIPHA");
        assertThat(number(f.get("close"))).isEqualByComparingTo("4.970");
        // Nothing follows the dividend date on a fixing line; the close is
        // the security's only price and the provider uses it as the last.
        assertThat(f.get("last").trim()).isEmpty();
    }

    @Test
    @DisplayName("reads a fixing line whose state flag abuts the ISIN")
    void parsesStateFlaggedLine() {
        Map<String, String> f = parse(UADH);

        assertThat(f.get("mnemo")).isEqualTo("UADH");
        assertThat(number(f.get("close"))).isEqualByComparingTo("0.430");
    }

    @Test
    @DisplayName("reads a line carrying four prices ahead of the ISIN")
    void parsesFourPriceLine() {
        Map<String, String> f = parse(SOTUMAG);

        assertThat(f.get("mnemo")).isEqualTo("MGR");
        assertThat(number(f.get("close"))).isEqualByComparingTo("23.590");
        // Four numbers, and the reference price leads here while it trails on
        // the Amen Bank line - so open/high/low are left uninterpreted rather
        // than guessed. Only the count is asserted.
        assertThat(f.get("head").trim().split("\\s+")).hasSize(4);
    }

    @Test
    @DisplayName("keeps the dividend date, which dates a lapsed payer")
    void parsesDividendDate() {
        Map<String, String> amen = parse(AMEN_BANK);
        assertThat(number(amen.get("dividend"))).isEqualByComparingTo("3.600");
        assertThat(date(amen.get("divdate"))).isEqualTo(LocalDate.of(2026, 5, 29));

        // ATB last paid in 2019 and SIPHAT in 2011. The amount alone would
        // read as a live dividend; the date is what distinguishes a current
        // payer from a figure left on the page for seven or fifteen years.
        assertThat(date(parse(ATB).get("divdate"))).isEqualTo(LocalDate.of(2019, 5, 9));
        assertThat(date(parse(SIPHAT).get("divdate"))).isEqualTo(LocalDate.of(2011, 7, 18));
    }

    @Test
    @DisplayName("ignores lines that are not quotes")
    void ignoresNonQuoteLines() {
        assertThat(parse("BULLETIN OFFICIEL")).isEmpty();
        assertThat(parse("8000 15 687,49 -0,63 %Indice des Societes Financieres 50,48 %")).isEmpty();
        assertThat(parse("Code ISIN Nominal ClturePlus Haut")).isEmpty();
    }
}
