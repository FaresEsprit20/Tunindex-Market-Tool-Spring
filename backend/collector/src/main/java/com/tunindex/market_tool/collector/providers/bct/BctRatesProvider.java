package com.tunindex.market_tool.collector.providers.bct;

import com.tunindex.market_tool.collector.dto.macro.MacroIndicatorDto;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Tunisia's headline interest rates from the central bank's own site.
 *
 * <p>The BCT front page carries them as plain sentences —
 * {@code <span>Taux d'intérêt directeur au 03/09/2026: <b>7,00000</b> %</span>}
 * — so each figure is located by matching its French label rather than by
 * position. Position would be quicker and would break silently the first time
 * the bank reorders the block or comments a line out (two of the six rates on
 * that page are already commented out).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BctRatesProvider {

    private static final String URL = "https://www.bct.gov.tn/bct/siteprod/index.jsp";
    private static final String SOURCE = "Banque Centrale de Tunisie";

    /**
     * Label fragment -> what we call it. Matched case-insensitively against
     * the sentence, so accents and the surrounding wording can drift a little
     * without breaking the lookup.
     */
    private record RateSpec(String key, String labelFragment, String label, String note) {
    }

    private static final List<RateSpec> RATES = List.of(
            new RateSpec("POLICY_RATE", "directeur", "Policy rate",
                    "The central bank's main rate — the anchor for borrowing costs across the economy."),
            new RateSpec("MONEY_MARKET_RATE", "marché monétaire (TM)", "Money market rate",
                    "What banks pay each other overnight; moves before retail rates do."),
            new RateSpec("TMM", "TMM", "TMM (monthly average)",
                    "The monthly average money-market rate most Tunisian loans are indexed to."),
            new RateSpec("SAVINGS_RATE", "épargne", "Savings rate",
                    "The regulated return on bank savings — equities compete with this for retail money."));

    /** "…: <b>7,00000</b> %" — the number sits in its own bold element. */
    private static final Pattern PERIOD = Pattern.compile("(au\\s+[0-9/]+|du mois de\\s+[^:]+?)\\s*:");

    private final WebClient webClient;

    public Mono<List<MacroIndicatorDto>> fetchRates() {
        return webClient.get()
                .uri(URL)
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parse)
                .timeout(Duration.ofSeconds(25))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .doOnError(e -> log.warn("BCT rates fetch failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private List<MacroIndicatorDto> parse(String html) {
        Document doc = Jsoup.parse(html);
        List<MacroIndicatorDto> indicators = new ArrayList<>();

        for (Element span : doc.select("span")) {
            String text = span.text();
            Element bold = span.selectFirst("b");
            if (bold == null || text.isBlank()) {
                continue;
            }

            BigDecimal value = parseNumber(bold.text());
            if (value == null) {
                continue;
            }

            RATES.stream()
                    .filter(spec -> text.toLowerCase().contains(spec.labelFragment().toLowerCase()))
                    // A sentence could in principle match two fragments; take
                    // the first declared, which is the more specific one.
                    .findFirst()
                    .ifPresent(spec -> {
                        if (indicators.stream().anyMatch(i -> i.getKey().equals(spec.key()))) {
                            return;
                        }
                        indicators.add(MacroIndicatorDto.builder()
                                .key(spec.key())
                                .label(spec.label())
                                .note(spec.note())
                                .value(value)
                                .unit("%")
                                .periodLabel(periodOf(text))
                                .source(SOURCE)
                                .sourceUrl(URL)
                                .build());
                    });
        }

        if (indicators.isEmpty()) {
            log.warn("BCT page parsed but no rates matched — the page layout may have changed");
        }
        return indicators;
    }

    /** The publisher's own period wording, kept verbatim. */
    private String periodOf(String sentence) {
        Matcher matcher = PERIOD.matcher(sentence);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** French decimal comma, and a value that may carry a trailing unit. */
    private BigDecimal parseNumber(String raw) {
        String cleaned = raw.replace(" ", "").replace(" ", "").replace(",", ".");
        Matcher matcher = Pattern.compile("^-?\\d+(?:\\.\\d+)?").matcher(cleaned);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group()).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
