package com.tunindex.market_tool.collector.providers.worldbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.tunindex.market_tool.collector.dto.macro.MacroIndicatorDto;
import com.tunindex.market_tool.collector.services.scraping.PageFetcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

/**
 * Annual national-accounts figures for Tunisia from the World Bank's open API.
 *
 * <p>Used for inflation and growth because Tunisia's own statistics institute
 * publishes them as PDFs and press releases rather than anything machine
 * readable. The trade-off is honest but real: these are <em>annual</em>
 * figures and can lag the current month by a long way, which is why the year
 * ships with every value and the UI shows it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorldBankProvider {

    private static final String BASE =
            "https://api.worldbank.org/v2/country/TUN/indicator/%s?format=json&per_page=10&mrv=10";
    private static final String SOURCE = "World Bank";
    private static final String SOURCE_URL = "https://data.worldbank.org/country/tunisia";

    /** {@code unit} matters: one of these is a percentage, the other is money. */
    private record Series(String key, String indicator, String label, String note, String unit) {
    }

    private static final List<Series> SERIES = List.of(
            new Series("INFLATION_CPI", "FP.CPI.TOTL.ZG", "Inflation (CPI)",
                    "Erodes real returns, and drives the central bank's rate decisions.", "%"),
            // External debt stocks, total, current US$. The central-government
            // debt series (GC.DOD.TOTL.GD.ZS) is the more conventional gauge
            // but the World Bank has no Tunisian value for it after 2012,
            // which is too stale to put on a dashboard.
            new Series("EXTERNAL_DEBT_USD", "DT.DOD.DECT.CD", "External debt",
                    "What the country owes abroad — the constraint behind fiscal and currency policy.",
                    "USD"));

    private final PageFetcher pageFetcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<List<MacroIndicatorDto>> fetchEconomy() {
        return Flux.fromIterable(SERIES)
                .concatMap(this::fetchOne)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("World Bank fetch failed: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    /**
     * Reads one indicator series.
     *
     * <p>Uses {@link PageFetcher}'s plain HTTP path rather than the shared
     * stealth {@code WebClient}. That client wraps every request in a chain of
     * emulation filters built for scraping HTML pages; pointed at a JSON API
     * it returned 200s whose bodies did not survive to the parser, and the
     * adaptive-delay filter in the same chain had previously backed off past
     * this method's timeout entirely. A public data API needs none of that —
     * it wants a plain, politely paced GET.
     */
    private Mono<MacroIndicatorDto> fetchOne(Series series) {
        return Mono.fromCallable(() -> {
                    String body = pageFetcher.fetchData(String.format(BASE, series.indicator()));
                    if (body == null || body.isBlank()) {
                        return null;
                    }
                    return toIndicator(series, objectMapper.readTree(body));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("World Bank series {} failed: {}", series.indicator(), e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Takes the most recent year that actually has a value. The API pads its
     * response with recent years whose value is null — taking element zero
     * blindly yields "no data" for a series that is perfectly well populated
     * two years back.
     */
    private MacroIndicatorDto toIndicator(Series series, JsonNode json) {
        if (!json.isArray() || json.size() < 2 || !json.get(1).isArray()) {
            return null;
        }

        for (JsonNode row : json.get(1)) {
            JsonNode value = row.path("value");
            if (value.isNull() || value.isMissingNode()) {
                continue;
            }
            return MacroIndicatorDto.builder()
                    .key(series.key())
                    .label(series.label())
                    .note(series.note())
                    .value(BigDecimal.valueOf(value.asDouble())
                            .setScale("%".equals(series.unit()) ? 2 : 0, RoundingMode.HALF_UP))
                    .unit(series.unit())
                    .periodLabel(row.path("date").asText())
                    .source(SOURCE)
                    .sourceUrl(SOURCE_URL)
                    .build();
        }
        return null;
    }
}
