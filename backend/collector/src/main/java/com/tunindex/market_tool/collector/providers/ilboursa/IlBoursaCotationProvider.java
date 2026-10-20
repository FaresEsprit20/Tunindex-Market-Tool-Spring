package com.tunindex.market_tool.collector.providers.ilboursa;

import com.tunindex.market_tool.collector.services.scraping.PageFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Year;

/**
 * The per-symbol quote page, {@code /marches/cotation_{TICKER}}, read for the
 * two things it publishes that nothing else we have does reliably.
 *
 * <p><b>The performance table</b> gives the high, low and change over several
 * horizons. Its "1 an" row is a 52-week high, a 52-week low and a one-year
 * return, stated by the venue itself:
 *
 * <pre>
 *   1 an | 10,15 | 5,17 | 59,92%
 * </pre>
 *
 * <p><b>The dividend table</b> gives the gross dividend per share by year.
 *
 * <p><b>What this deliberately does not read is that table's "Rendement"
 * column.</b> It looks exactly like a dividend yield and is not one: for BT it
 * shows 6,74% for 2025, while the dividend of 0,35 against the current price
 * of 8,30 is 4,22%. The site computes it against the price at distribution -
 * near the year's low - so copying the column would overstate the yield by
 * more than half. Only the amount is taken, and the yield is computed here
 * against our own live price.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IlBoursaCotationProvider {

    private static final String BASE_URL = "https://www.ilboursa.com/marches/cotation_";

    /** The performance row covering twelve months. */
    private static final String ONE_YEAR_LABEL = "1 an";

    /**
     * How old the newest dividend row may be.
     *
     * <p>A company that has stopped paying leaves its last coupon on the page,
     * and presenting it as the current dividend would invent an income stream
     * that no longer exists.
     */
    private static final int MAX_DIVIDEND_AGE_YEARS = 2;

    private final PageFetcher pageFetcher;

    public record Cotation(
            String symbol,
            BigDecimal week52High,
            BigDecimal week52Low,
            BigDecimal oneYearReturnPct,
            BigDecimal dividendPerShare,
            Integer dividendYear) {
    }

    public Cotation fetch(String symbol) {
        try {
            String html = pageFetcher.fetch(BASE_URL + symbol);
            if (html == null || html.isBlank()) {
                return null;
            }

            Document doc = Jsoup.parse(html);
            Elements rows = doc.select("tr");

            BigDecimal high = null;
            BigDecimal low = null;
            BigDecimal oneYear = null;

            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 4) {
                    continue;
                }
                if (!ONE_YEAR_LABEL.equalsIgnoreCase(
                        IlBoursaNumbers.normaliseSpaces(cells.get(0).text()))) {
                    continue;
                }
                high = IlBoursaNumbers.parse(cells.get(1).text());
                low = IlBoursaNumbers.parse(cells.get(2).text());
                oneYear = IlBoursaNumbers.parse(cells.get(3).text());
                break;
            }

            Dividend dividend = latestDividend(rows);

            if (high == null && low == null && oneYear == null && dividend == null) {
                return null;
            }

            return new Cotation(symbol, high, low, oneYear,
                    dividend == null ? null : dividend.amount(),
                    dividend == null ? null : dividend.year());

        } catch (Exception e) {
            log.warn("ilboursa cotation failed for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private record Dividend(int year, BigDecimal amount) {
    }

    /**
     * The most recent dividend row, if it is recent enough to still describe
     * the company.
     *
     * <p>Rows are "Année | Montant | Rendement" and are listed newest first,
     * but that is not relied on - the largest year found wins, so a change of
     * ordering cannot quietly select an old coupon.
     */
    private Dividend latestDividend(Elements rows) {
        Dividend best = null;

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 2) {
                continue;
            }
            String yearText = IlBoursaNumbers.normaliseSpaces(cells.get(0).text());
            if (!yearText.matches("(19|20)\\d{2}")) {
                continue;
            }
            BigDecimal amount = IlBoursaNumbers.parse(cells.get(1).text());
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            int year = Integer.parseInt(yearText);
            if (best == null || year > best.year()) {
                best = new Dividend(year, amount);
            }
        }

        if (best == null) {
            return null;
        }
        if (Year.now().getValue() - best.year() > MAX_DIVIDEND_AGE_YEARS) {
            log.debug("ilboursa cotation: newest dividend is {} - too old to use", best.year());
            return null;
        }
        return best;
    }
}
