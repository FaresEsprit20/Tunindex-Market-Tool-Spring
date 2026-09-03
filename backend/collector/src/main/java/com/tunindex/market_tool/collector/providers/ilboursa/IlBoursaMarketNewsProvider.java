package com.tunindex.market_tool.collector.providers.ilboursa;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Market-wide news from ilboursa's exchange news page.
 *
 * <p>Different markup from the per-stock feed handled by
 * {@link IlBoursaNewsProvider}: this page is a table where each row carries
 * the timestamp, the headline, and the related stock's price and day move —
 * so a market headline arrives already paired with what the market did
 * about it. Note the date here is a four-digit year (dd/MM/yyyy), unlike
 * the two-digit year on the per-stock pages.
 */
@Slf4j
@Component
public class IlBoursaMarketNewsProvider {

    private static final String URL = "https://www.ilboursa.com/marches/actualites_bourse_tunis";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final WebClient webClient;

    public IlBoursaMarketNewsProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * @param relatedChangePct day move of the stock the row is filed under,
     *                         null when the row carries no quote cell.
     */
    public record MarketNewsItem(
            String headline,
            String url,
            LocalDateTime publishedAt,
            BigDecimal relatedPrice,
            BigDecimal relatedChangePct) {
    }

    public Mono<List<MarketNewsItem>> fetchMarketNews() {
        return webClient.get()
                .uri(URL)
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parse)
                .timeout(Duration.ofSeconds(20))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .doOnError(e -> log.warn("ilboursa market news fetch failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private List<MarketNewsItem> parse(String html) {
        Document doc = Jsoup.parse(html);
        List<MarketNewsItem> items = new ArrayList<>();

        Elements rows = doc.select("#tabQuotes tbody tr");
        if (rows.isEmpty()) {
            log.warn("Market news page returned no rows — markup may have changed");
            return items;
        }

        for (Element row : rows) {
            Element dateCell = row.selectFirst("span.sp1");
            Element link = row.selectFirst("a[href]");
            if (dateCell == null || link == null) {
                continue;
            }

            LocalDateTime publishedAt;
            try {
                publishedAt = LocalDateTime.parse(dateCell.text().trim(), DATE_FORMAT);
            } catch (Exception e) {
                log.debug("Skipping market news row with unparseable date '{}'", dateCell.text());
                continue;
            }

            String headline = link.text().trim();
            if (headline.isEmpty()) {
                continue;
            }

            String href = link.attr("href");
            String absoluteUrl = href.startsWith("http") ? href : "https://www.ilboursa.com" + href;

            Element quoteCell = row.selectFirst("td.alri");
            BigDecimal price = null;
            BigDecimal changePct = null;
            if (quoteCell != null) {
                price = parseNumber(quoteCell.ownText());
                Element changeSpan = quoteCell.selectFirst("span[class^=quote_]");
                if (changeSpan != null) {
                    changePct = parseNumber(changeSpan.text().replace("%", ""));
                }
            }

            items.add(new MarketNewsItem(headline, absoluteUrl, publishedAt, price, changePct));
        }

        log.debug("Parsed {} market news items", items.size());
        return items;
    }

    /** The site writes numbers French-style: comma decimal separator, spaces as grouping. */
    private BigDecimal parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace(" ", "").replace(" ", "").replace(",", ".").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
