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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The whole cote in a single request, from {@code /marches/aaz}.
 *
 * <p>This is the cheapest source we have: one GET returns a row per listed
 * security - around ninety of them - with open, high, low, last, volume in
 * shares, volume in dinars and the day's change. Fetching the same coverage
 * per-symbol would be ninety requests against a small site.
 *
 * <p>Its value as a fallback is breadth rather than depth. The primary source
 * has no page at all for a dozen listed companies, and no amount of parsing
 * reaches them; this table has every one. It carries no fundamentals, so it
 * fills price and volume only.
 *
 * <p>Rows are keyed by the ticker taken from each row's own
 * {@code cotation_{TICKER}} link rather than by the displayed name. The names
 * are trade names ("AMEN BANK", "AIR LIQUIDE TUNISIE") and matching on them
 * would be guesswork; every row on the page carries the link.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IlBoursaMarketTableProvider {

    private static final String URL = "https://www.ilboursa.com/marches/aaz";

    private static final Pattern TICKER_PATTERN = Pattern.compile("cotation_([A-Z0-9]+)");

    /** Column order of the table, after the name cell. */
    private static final int OPEN = 1;
    private static final int HIGH = 2;
    private static final int LOW = 3;
    private static final int VOLUME_SHARES = 4;
    private static final int VOLUME_DINARS = 5;
    private static final int LAST = 6;
    private static final int CHANGE_PCT = 7;
    private static final int EXPECTED_CELLS = 8;

    private final PageFetcher pageFetcher;

    public record MarketRow(
            String ticker,
            String name,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal last,
            Long volumeShares,
            BigDecimal volumeDinars,
            BigDecimal changePct) {
    }

    /** Every row on the cote, keyed by ticker. Empty if the page is unusable. */
    public Map<String, MarketRow> fetchAll() {
        Map<String, MarketRow> rows = new LinkedHashMap<>();
        try {
            String html = pageFetcher.fetch(URL);
            if (html == null || html.isBlank()) {
                log.warn("ilboursa cote table: empty page");
                return rows;
            }

            Document doc = Jsoup.parse(html);
            for (Element row : doc.select("tr")) {
                MarketRow parsed = parseRow(row);
                if (parsed != null) {
                    rows.put(parsed.ticker(), parsed);
                }
            }
            log.info("ilboursa cote table: read {} listings", rows.size());
        } catch (Exception e) {
            log.warn("ilboursa cote table failed: {}", e.getMessage());
        }
        return rows;
    }

    private MarketRow parseRow(Element row) {
        Matcher ticker = TICKER_PATTERN.matcher(row.html());
        if (!ticker.find()) {
            // The header row and the page's navigation rows have no link.
            return null;
        }

        Elements cells = row.select("td");
        if (cells.size() < EXPECTED_CELLS) {
            return null;
        }

        BigDecimal last = IlBoursaNumbers.parse(cells.get(LAST).text());
        if (last == null || last.signum() <= 0) {
            // A row with no last price is a suspended or never-traded line;
            // storing a zero would read as a real quote of zero.
            return null;
        }

        return new MarketRow(
                ticker.group(1),
                IlBoursaNumbers.normaliseSpaces(cells.get(0).text()),
                IlBoursaNumbers.parse(cells.get(OPEN).text()),
                IlBoursaNumbers.parse(cells.get(HIGH).text()),
                IlBoursaNumbers.parse(cells.get(LOW).text()),
                last,
                IlBoursaNumbers.parseLong(cells.get(VOLUME_SHARES).text()),
                IlBoursaNumbers.parse(cells.get(VOLUME_DINARS).text()),
                IlBoursaNumbers.parse(cells.get(CHANGE_PCT).text()));
    }
}
