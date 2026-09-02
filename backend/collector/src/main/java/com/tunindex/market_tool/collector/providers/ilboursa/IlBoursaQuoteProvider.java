package com.tunindex.market_tool.collector.providers.ilboursa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * ilboursa.com's plain quote page (no CSRF form, unlike the CSV download) --
 * a genuine live snapshot for the current session. Used as the authoritative
 * source for today's price/volume fields: stockanalysis.com (the main
 * pipeline's source) was found to lag by a full trading day for this
 * market, confirmed by comparing its "current" price against both
 * ilboursa's live page and our own scraped history for the same date.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IlBoursaQuoteProvider {

    private static final String BASE_URL = "https://www.ilboursa.com/marches/cotation_";

    // Strips every kind of whitespace Jsoup might hand back for these
    // number fields, including &#xA0; (U+00A0 non-breaking space) which a
    // plain ASCII-space replace silently fails to remove.
    private static final Pattern ANY_WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

    private final WebClient webClient;

    public record LiveQuote(BigDecimal lastPrice, BigDecimal open, BigDecimal prevClose,
                             BigDecimal dayHigh, BigDecimal dayLow, Long volume) {
    }

    public Mono<LiveQuote> fetchQuote(String symbol) {
        return webClient.get()
                .uri(BASE_URL + symbol)
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> parse(symbol, html))
                .timeout(Duration.ofSeconds(20))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .doOnError(e -> log.warn("ilboursa live quote failed for {}: {}", symbol, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private LiveQuote parse(String symbol, String html) {
        Document doc = Jsoup.parse(html);

        BigDecimal lastPrice = textOf(doc.selectFirst(".cot_v1b"));

        Element leftBlock = doc.selectFirst(".cot_v2 .cot_v21");
        Element rightBlock = doc.selectFirst(".cot_v2 .cot_v22");
        BigDecimal open = nthValue(leftBlock, 0);
        BigDecimal dayHigh = nthValue(leftBlock, 1);
        BigDecimal prevClose = nthValue(rightBlock, 0);
        BigDecimal dayLow = nthValue(rightBlock, 1);

        Long volume = longOf(doc.selectFirst("#vol"));

        if (lastPrice == null) {
            log.debug("Could not parse live quote for {} (page may not have loaded expected markup)", symbol);
        }

        return new LiveQuote(lastPrice, open, prevClose, dayHigh, dayLow, volume);
    }

    /**
     * Inside .cot_v21 / .cot_v22, direct children alternate label/value
     * pairs (label, value, label, value); index 0 = first value, index 1 =
     * second value.
     */
    private BigDecimal nthValue(Element container, int valueIndex) {
        if (container == null) return null;
        var children = container.children();
        int pos = valueIndex * 2 + 1;
        if (pos >= children.size()) return null;
        return textOf(children.get(pos));
    }

    private BigDecimal textOf(Element el) {
        if (el == null) return null;
        String raw = stripWhitespace(el.text());
        if (raw.isEmpty()) return null;
        try {
            return new BigDecimal(raw.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long longOf(Element el) {
        if (el == null) return null;
        String raw = stripWhitespace(el.text());
        if (raw.isEmpty()) return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stripWhitespace(String raw) {
        return ANY_WHITESPACE.matcher(raw).replaceAll("");
    }
}
