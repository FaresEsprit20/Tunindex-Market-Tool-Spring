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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * ilboursa.com's per-stock news feed (marches/news_valeur?s=SYMBOL) — real
 * editorial headlines with real publish timestamps, page 1 only (most
 * recent ~20 items; older pages exist but aren't fetched, matching the
 * "latest news" scope of every other real-time feature here).
 */
@Slf4j
@Component
public class IlBoursaNewsProvider {

    private static final String BASE_URL = "https://www.ilboursa.com/marches/news_valeur?s=";
    private static final String ARTICLE_BASE_URL = "https://www.ilboursa.com/marches/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final WebClient webClient;

    public IlBoursaNewsProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    public record NewsItem(String headline, String url, LocalDateTime publishedAt) {
    }

    public Mono<List<NewsItem>> fetchNews(String symbol) {
        return webClient.get()
                .uri(BASE_URL + symbol)
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> parse(symbol, html))
                .timeout(Duration.ofSeconds(20))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .doOnError(e -> log.warn("ilboursa news fetch failed for {}: {}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private List<NewsItem> parse(String symbol, String html) {
        Document doc = Jsoup.parse(html);
        Element container = doc.selectFirst(".home_content");
        List<NewsItem> items = new ArrayList<>();
        if (container == null) {
            log.debug("No news container found for {} (page may not have loaded expected markup)", symbol);
            return items;
        }

        Elements dates = container.select("span.sp1");
        Elements links = container.select("a[href]");

        int count = Math.min(dates.size(), links.size());
        for (int i = 0; i < count; i++) {
            String rawDate = dates.get(i).text().trim();
            Element link = links.get(i);
            String headline = link.text().trim();
            String href = link.attr("href").trim();

            if (headline.isEmpty() || href.isEmpty()) {
                continue;
            }

            LocalDateTime publishedAt;
            try {
                publishedAt = LocalDateTime.parse(rawDate, DATE_FORMAT);
            } catch (DateTimeParseException e) {
                log.debug("Could not parse news date '{}' for {}", rawDate, symbol);
                continue;
            }

            String fullUrl = href.startsWith("http") ? href : ARTICLE_BASE_URL + href;
            items.add(new NewsItem(headline, fullUrl, publishedAt));
        }

        return items;
    }
}
