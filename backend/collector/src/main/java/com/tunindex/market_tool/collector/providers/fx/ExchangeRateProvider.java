package com.tunindex.market_tool.collector.providers.fx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Live TND exchange rates from exchangerate-api.com's free, key-less
 * endpoint (base=TND, updated daily) — a real third-party FX feed, not a
 * scrape or a fabricated table. Cached in memory for CACHE_TTL since the
 * upstream source itself only refreshes once a day; this just avoids
 * hammering it on every request.
 */
@Slf4j
@Component
public class ExchangeRateProvider {

    private static final String URL = "https://open.er-api.com/v6/latest/TND";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final WebClient webClient;
    private final AtomicReference<CachedRates> cache = new AtomicReference<>();

    public ExchangeRateProvider(WebClient webClient) {
        this.webClient = webClient;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RawResponse {
        private String result;
        private String baseCode;
        private Map<String, BigDecimal> rates;
    }

    private record CachedRates(Map<String, BigDecimal> tndToForeign, LocalDateTime fetchedAt) {
    }

    /** TND value of one unit of each foreign currency (rates.get("USD") = TND per 1 USD, etc.). */
    public Mono<Map<String, BigDecimal>> fetchRatesInTnd() {
        CachedRates cached = cache.get();
        if (cached != null && Duration.between(cached.fetchedAt(), LocalDateTime.now()).compareTo(CACHE_TTL) < 0) {
            return Mono.just(cached.tndToForeign());
        }

        return webClient.get()
                .uri(URL)
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .bodyToMono(RawResponse.class)
                .map(this::invertToTnd)
                .doOnNext(rates -> cache.set(new CachedRates(rates, LocalDateTime.now())))
                .timeout(Duration.ofSeconds(15))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .doOnError(e -> log.warn("Exchange rate fetch failed: {}", e.getMessage()))
                .onErrorResume(e -> cached != null ? Mono.just(cached.tndToForeign()) : Mono.empty());
    }

    private Map<String, BigDecimal> invertToTnd(RawResponse raw) {
        Map<String, BigDecimal> tndPerForeign = new java.util.LinkedHashMap<>();
        if (raw.getRates() == null) {
            return tndPerForeign;
        }
        raw.getRates().forEach((code, tndValueOfOneUnit) -> {
            // API gives "1 TND = X <code>" (base=TND); invert to "1 <code> = Y TND".
            if (tndValueOfOneUnit != null && tndValueOfOneUnit.compareTo(BigDecimal.ZERO) > 0) {
                tndPerForeign.put(code, BigDecimal.ONE.divide(tndValueOfOneUnit, 6, java.math.RoundingMode.HALF_UP));
            }
        });
        return tndPerForeign;
    }
}
