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
import java.math.RoundingMode;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Second source for company fundamentals, read from ilboursa's
 * {@code /marches/societe/{TICKER}} page.
 *
 * <p>Exists because the primary source is not complete for this market:
 * stockanalysis.com has no page at all for a dozen listed companies, and
 * reports {@code n/a} for individual figures on others. This provider is only
 * ever asked for fields the primary left blank - it does not overwrite a
 * figure we already have, because two sources computing the same ratio over
 * different periods will disagree, and silently swapping one for the other
 * would make a stock's numbers change for no visible reason.
 *
 * <p>The page publishes a five-year table:
 *
 * <pre>
 *              | 2021    | 2022    | 2023    | 2024    | 2025
 *   CA         | 385 983 | 428 815 | 493 636 | 518 336 | 547 525
 *   Résultat   | 152 124 | 168 562 | 182 322 | 199 437 | 202 819
 *   BNPA       | 0,68    | 0,62    | 0,68    | 0,74    | 0,75
 *   PER        | 12,28   | 13,30   | 12,30   | 11,23   | 11,05
 *   Dividende  | 0,28    | 0,28    | 0,29    | 0,35    | 0,35
 * </pre>
 *
 * <p><b>Freshness is the thing to get right here.</b> The most recent column
 * is not always recent: a company that stopped publishing keeps its last
 * filing on the page indefinitely - ADWYA's newest column is 2022. Taking the
 * rightmost value unconditionally would quietly file a four-year-old EPS as
 * current, which is worse than the blank it replaced, because nothing
 * downstream could tell. So the year header is parsed and anything older than
 * {@link #MAX_FISCAL_YEAR_AGE} years is refused.
 *
 * <p>Figures are French-formatted: comma decimal separator, non-breaking
 * spaces between thousands, and "-" for a value the company did not report.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IlBoursaFundamentalsProvider {

    private static final String BASE_URL = "https://www.ilboursa.com/marches/societe/";

    /**
     * How many years behind the current one a fiscal column may be.
     *
     * <p>Two, not one: annual results for the year just ended are published
     * partway through the following year, so in early 2026 the newest
     * available column is legitimately 2024 for many companies. Three would
     * start admitting genuinely abandoned filings.
     */
    private static final int MAX_FISCAL_YEAR_AGE = 2;

    /**
     * The share count and the number that follows it on the same line.
     *
     * <p>Digits and spaces only - a share count is a whole number, so allowing
     * a decimal separator here would let the free-float percentage that
     * follows bleed into the match.
     */
    private static final Pattern SHARES_PATTERN =
            Pattern.compile("Nombre de titres\\s*:?\\s*([0-9][0-9\\s]*)");

    /** The table's monetary rows are printed in thousands of dinars. */
    private static final BigDecimal THOUSANDS = new BigDecimal("1000");

    private final PageFetcher pageFetcher;

    /**
     * What this source can offer for one company. Every field is nullable -
     * the page carries a row only when the company reported that figure.
     *
     * @param fiscalYear the year the figures belong to, so callers can say
     *                   where a number came from and how old it is
     */
    public record Fundamentals(
            String symbol,
            Integer fiscalYear,
            BigDecimal eps,
            BigDecimal peRatio,
            BigDecimal dividendPerShare,
            BigDecimal revenue,
            BigDecimal netIncome,
            BigDecimal profitMargin,
            Long sharesOutstanding) {
    }

    /** Returns what the page holds, or {@code null} if it holds nothing usable. */
    public Fundamentals fetch(String symbol) {
        try {
            String html = pageFetcher.fetch(BASE_URL + symbol);
            if (html == null || html.isBlank()) {
                log.debug("ilboursa fundamentals: empty page for {}", symbol);
                return null;
            }
            return parse(symbol, html);
        } catch (Exception e) {
            log.warn("ilboursa fundamentals failed for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    private Fundamentals parse(String symbol, String html) {
        Document doc = Jsoup.parse(html);

        List<Integer> years = null;
        Elements rows = doc.select("tr");

        // The header is the first row whose cells are all four-digit years.
        for (Element row : rows) {
            List<Integer> candidate = yearHeader(row);
            if (candidate != null && candidate.size() >= 2) {
                years = candidate;
                break;
            }
        }
        if (years == null) {
            log.debug("ilboursa fundamentals: no year header for {}", symbol);
            return null;
        }

        BigDecimal[] eps = rowValues(rows, "BNPA", years.size());
        BigDecimal[] per = rowValues(rows, "PER", years.size());
        BigDecimal[] dividend = rowValues(rows, "Dividende", years.size());
        BigDecimal[] revenue = rowValues(rows, "Chiffre d'affaires", years.size());
        BigDecimal[] netIncome = rowValues(rows, "Résultat net", years.size());

        // Anchor on EPS: it is the figure the rest are checked against, and a
        // column with no EPS is not a reporting year worth reading.
        int column = latestPopulatedColumn(eps, per, revenue);
        if (column < 0) {
            log.debug("ilboursa fundamentals: no populated column for {}", symbol);
            return null;
        }

        Integer fiscalYear = years.get(column);
        int age = Year.now().getValue() - fiscalYear;
        if (age > MAX_FISCAL_YEAR_AGE) {
            log.info("ilboursa fundamentals for {} stop at {} ({} years old) - refusing as stale",
                    symbol, fiscalYear, age);
            return null;
        }

        BigDecimal revenueAbsolute = scale(revenue[column]);
        BigDecimal netIncomeAbsolute = scale(netIncome[column]);

        return new Fundamentals(
                symbol,
                fiscalYear,
                eps[column],
                per[column],
                dividend[column],
                revenueAbsolute,
                netIncomeAbsolute,
                profitMargin(netIncomeAbsolute, revenueAbsolute),
                sharesOutstanding(doc));
    }

    /** The row of fiscal years, or null if this row is not that header. */
    private List<Integer> yearHeader(Element row) {
        Elements cells = row.select("td, th");
        List<Integer> years = new ArrayList<>();
        boolean sawNonYear = false;

        for (Element cell : cells) {
            String text = clean(cell.text());
            if (text.isEmpty()) {
                // The leading corner cell above the row labels is blank.
                continue;
            }
            if (text.matches("(19|20)\\d{2}")) {
                years.add(Integer.parseInt(text));
            } else {
                sawNonYear = true;
            }
        }
        return sawNonYear || years.isEmpty() ? null : years;
    }

    /**
     * The value cells of the row whose first cell is {@code label}, aligned to
     * the year columns.
     */
    private BigDecimal[] rowValues(Elements rows, String label, int columns) {
        BigDecimal[] values = new BigDecimal[columns];

        for (Element row : rows) {
            Elements cells = row.select("td, th");
            if (cells.size() < 2) {
                continue;
            }
            if (!clean(cells.get(0).text()).equalsIgnoreCase(label)) {
                continue;
            }
            // Cells after the label line up with the year columns; a short row
            // (a company reporting fewer years) simply leaves the rest null.
            for (int i = 1; i < cells.size() && i - 1 < columns; i++) {
                values[i - 1] = parseFrenchNumber(cells.get(i).text());
            }
            break;
        }
        return values;
    }

    /** The rightmost column for which any of the given rows has a value. */
    private int latestPopulatedColumn(BigDecimal[]... rows) {
        int columns = rows.length == 0 ? 0 : rows[0].length;
        for (int column = columns - 1; column >= 0; column--) {
            for (BigDecimal[] row : rows) {
                if (row[column] != null) {
                    return column;
                }
            }
        }
        return -1;
    }

    /**
     * "Nombre de titres : 270 000 000" sits outside the table, inline with the
     * free-float figure that follows it.
     *
     * <p>Captured with an explicit pattern rather than by taking a slice of
     * text after the label: the next words are "Flottant : 50,00%", and a
     * looser read that strips separators before isolating the number turns
     * "270 000 000 Flottant : 50,00%" into one unparseable run of characters.
     */
    private Long sharesOutstanding(Document doc) {
        Matcher matcher = SHARES_PATTERN.matcher(clean(doc.text()));
        if (!matcher.find()) {
            return null;
        }
        BigDecimal shares = parseFrenchNumber(matcher.group(1));
        if (shares == null || shares.signum() <= 0) {
            return null;
        }
        return shares.longValue();
    }

    private BigDecimal profitMargin(BigDecimal netIncome, BigDecimal revenue) {
        if (netIncome == null || revenue == null || revenue.signum() <= 0) {
            return null;
        }
        return netIncome.divide(revenue, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal thousands) {
        return thousands == null ? null : thousands.multiply(THOUSANDS);
    }

    /**
     * Parses "1 234,56", "-7 272", "12,30%" and "-" (meaning not reported).
     *
     * <p>The separators matter: a plain {@code new BigDecimal(text)} on
     * "108 179" throws, and on "0,15" throws as well, so a French page read
     * with an English parser yields nothing at all rather than wrong numbers -
     * which is how this went unnoticed as a possibility.
     */
    private BigDecimal parseFrenchNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String text = clean(raw)
                .replace("%", "")
                .replace(" ", "")
                .replace(" ", "")
                .replace(" ", "")
                .replace(",", ".")
                .trim();

        if (text.isEmpty() || text.equals("-") || text.equalsIgnoreCase("n/a") || text.equals("--")) {
            return null;
        }
        // A stray label or footnote marker should be skipped, not guessed at.
        if (!text.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Normalises the several kinds of space this page uses. */
    private String clean(String text) {
        return text == null ? "" : text
                .replace(" ", " ")
                .replace(" ", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
