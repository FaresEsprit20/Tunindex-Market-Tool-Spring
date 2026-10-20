package com.tunindex.market_tool.collector.providers.bvmt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The exchange's own daily bulletin - the legal record of each session.
 *
 * <p>Every other source we have is a third party republishing this. That makes
 * it the one fallback that cannot quietly go stale: it is published by the
 * venue, once per session, and if today's file exists it is today's data by
 * definition. The public sites that mirror BVMT data were measured against the
 * live market and found to be months behind while still looking current, which
 * is precisely the failure this source is immune to.
 *
 * <p>It is also the only source carrying the ISIN, the nominal, the trading
 * group and the last dividend together with its date, and it covers every
 * listed company - including the dozen the primary source has no page for.
 *
 * <p><b>Location.</b> The bulletin moved when the exchange replaced its site;
 * the old {@code bvmt.com.tn/sites/default/files/bulletin/pdf/bullYYYYMMDD.pdf}
 * path now returns an empty document. The current form is dated by month
 * folder, which is why the URL is built from the session date rather than
 * being a constant.
 *
 * <p><b>Dates.</b> There is no bulletin on a weekend or a public holiday, so a
 * missing file is normal rather than an error. The most recent one is found by
 * walking back day by day within a short window.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BvmtBulletinProvider {

    private static final String BASE = "https://tunis-stockexchange.com/sites/default/files/";

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * How many days back to look for the latest bulletin.
     *
     * <p>Enough to clear a long weekend plus a public holiday, and short
     * enough that a prolonged outage surfaces as "no bulletin" rather than as
     * silently week-old prices.
     */
    private static final int MAX_LOOKBACK_DAYS = 6;

    /**
     * A quote line, as the extractor lays it out:
     *
     * <pre>
     * AMEN BANK 91,000 91,190 89,500TN0003400058 90,500AB 5,000 3,600 29/05/2026 90,500Groupe 11 - Continu 91,0001
     * </pre>
     *
     * <p>Anchored on the ISIN and the mnemonic rather than on column
     * positions: the PDF's header spans several lines and the extractor
     * interleaves them, so counting columns from the header is unreliable
     * while these two anchors are unambiguous.
     */
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

    /** The prices ahead of the ISIN, when there are exactly the usual three. */
    private static final Pattern HEAD_NUMBERS = Pattern.compile("[\\d][\\d\\s.,]*");

    private static final DateTimeFormatter DIVIDEND_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /**
     * One security's line in the bulletin.
     *
     * @param lastDividend     gross amount per share
     * @param lastDividendDate when that dividend went ex - an old date is the
     *                         source telling us the company has stopped paying
     */
    public record Quote(
            String mnemo,
            String name,
            String isin,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal last,
            BigDecimal nominal,
            BigDecimal lastDividend,
            LocalDate lastDividendDate) {
    }

    public record Bulletin(LocalDate sessionDate, Map<String, Quote> quotes) {
    }

    /** The most recent bulletin available, or null if none is reachable. */
    public Bulletin fetchLatest() {
        LocalDate day = LocalDate.now();
        for (int i = 0; i < MAX_LOOKBACK_DAYS; i++, day = day.minusDays(1)) {
            Bulletin bulletin = fetch(day);
            if (bulletin != null) {
                return bulletin;
            }
        }
        log.warn("No BVMT bulletin found in the last {} days", MAX_LOOKBACK_DAYS);
        return null;
    }

    /** The bulletin for one session, or null when none was published. */
    public Bulletin fetch(LocalDate session) {
        String url = BASE + session.format(FOLDER) + "/Bull" + session.format(FILE) + ".pdf";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(45))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Accept", "application/pdf,*/*")
                    .GET()
                    .build();

            HttpResponse<byte[]> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200 || response.body() == null || response.body().length < 10_000) {
                // Weekends, holidays and not-yet-published sessions all land
                // here; it is the normal case, not a failure.
                log.debug("No bulletin for {} (status {})", session, response.statusCode());
                return null;
            }

            Map<String, Quote> quotes = parse(response.body());
            if (quotes.isEmpty()) {
                log.warn("Bulletin for {} parsed to no quotes", session);
                return null;
            }

            log.info("BVMT bulletin {}: {} securities", session, quotes.size());
            return new Bulletin(session, quotes);

        } catch (Exception e) {
            log.debug("Bulletin fetch failed for {}: {}", session, e.getMessage());
            return null;
        }
    }

    private Map<String, Quote> parse(byte[] pdf) throws Exception {
        Map<String, Quote> quotes = new LinkedHashMap<>();

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdf))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            for (String rawLine : text.split("\\R")) {
                String line = normalise(rawLine);
                Matcher matcher = QUOTE_LINE.matcher(line);
                if (!matcher.find()) {
                    continue;
                }

                BigDecimal close = number(matcher.group("close"));
                if (close == null || close.signum() <= 0) {
                    continue;
                }

                // A security quoted at a fixing has no separate last price -
                // the line ends at the dividend date. Its close is its only
                // price, and using it is correct rather than a fallback.
                BigDecimal last = number(matcher.group("last"));
                if (last == null || last.signum() <= 0) {
                    last = close;
                }

                // Only the three-price form has an unambiguous open/high/low.
                // The other layouts carry the reference price in a different
                // position, and there is no way to tell which number is which
                // without guessing - so those are left null rather than filled
                // with a plausible-looking mistake.
                BigDecimal open = null;
                BigDecimal high = null;
                BigDecimal low = null;
                List<BigDecimal> head = headNumbers(matcher.group("head"));
                if (head.size() == 3) {
                    open = head.get(0);
                    high = head.get(1);
                    low = head.get(2);
                }

                quotes.put(matcher.group("mnemo"), new Quote(
                        matcher.group("mnemo"),
                        matcher.group("name").trim(),
                        matcher.group("isin"),
                        open,
                        high,
                        low,
                        close,
                        last,
                        number(matcher.group("nominal")),
                        number(matcher.group("dividend")),
                        date(matcher.group("divdate"))));
            }
        }
        return quotes;
    }

    /**
     * The bulletin separates thousands with a non-breaking space and uses a
     * comma for decimals, so "15 687,49" must not reach a parser expecting
     * either convention on its own.
     */
    private String normalise(String line) {
        return line == null ? "" : line
                .replace(' ', ' ')
                .replace(' ', ' ')
                .replace(' ', ' ')
                .trim();
    }

    /**
     * The distinct numbers in the segment ahead of the ISIN.
     *
     * <p>Their count is what identifies the layout: three means the ordinary
     * continuous quote, anything else means a form whose column order differs,
     * and the caller declines to interpret those.
     */
    private List<BigDecimal> headNumbers(String head) {
        List<BigDecimal> numbers = new ArrayList<>();
        if (head == null) {
            return numbers;
        }
        Matcher matcher = HEAD_NUMBERS.matcher(head.trim());
        while (matcher.find()) {
            for (String token : matcher.group().trim().split("\s+")) {
                BigDecimal value = number(token);
                if (value != null) {
                    numbers.add(value);
                }
            }
        }
        return numbers;
    }

    private BigDecimal number(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace(" ", "").replace(",", ".").trim();
        if (text.isEmpty() || !text.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate date(String raw) {
        try {
            return raw == null ? null : LocalDate.parse(raw, DIVIDEND_DATE);
        } catch (Exception e) {
            return null;
        }
    }
}
