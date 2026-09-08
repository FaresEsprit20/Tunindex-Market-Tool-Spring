package com.tunindex.market_tool.collector.providers.worldbank;

import com.fasterxml.jackson.databind.JsonNode;
import com.tunindex.market_tool.collector.dto.macro.MacroIndicatorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    private record Series(String key, String indicator, String label, String note) {
    }

    private static final List<Series> SERIES = List.of(
            new Series("INFLATION_CPI", "FP.CPI.TOTL.ZG", "Inflation (CPI)",
                    "Erodes real returns, and drives the central bank's rate decisions."),
            new Series("GDP_GROWTH", "NY.GDP.MKTP.KD.ZG", "GDP growth",
                    "The demand backdrop behind company earnings."));

    private final WebClient webClient;

    public Mono<List<MacroIndicatorDto>> fetchEconomy() {
        return Flux.fromIterable(SERIES)
                .concatMap(this::fetchOne)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("World Bank fetch failed: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Mono<MacroIndicatorDto> fetchOne(Series series) {
        return webClient.get()
                .uri(String.format(BASE, series.indicator()))
                // The shared WebClient has no decompressor configured, so a
                // gzipped body arrives as raw bytes and Jackson fails on the
                // 0x1F magic byte. Asking for identity is simpler than wiring
                // compression support for two small JSON documents.
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(20))
                .mapNotNull(json -> toIndicator(series, json))
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
                    .value(BigDecimal.valueOf(value.asDouble()).setScale(2, RoundingMode.HALF_UP))
                    .unit("%")
                    .periodLabel(row.path("date").asText())
                    .source(SOURCE)
                    .sourceUrl(SOURCE_URL)
                    .build();
        }
        return null;
    }
}
