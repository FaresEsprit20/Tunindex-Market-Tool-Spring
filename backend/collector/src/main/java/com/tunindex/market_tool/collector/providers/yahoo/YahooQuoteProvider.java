package com.tunindex.market_tool.collector.providers.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.collector.dto.macro.MarketQuoteDto;
import com.tunindex.market_tool.collector.services.scraping.PageFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

/**
 * Prices for the instruments outside the BVMT that still move this market:
 * the metals, the major crypto pairs, and the dinar's two important crosses.
 *
 * <p>All of them come from one provider on purpose. Each quote carries a
 * "daily change", and that phrase has to mean the same thing across a banner —
 * here it is always last price against the previous session's close. Sourcing
 * crypto from an exchange API that reports a rolling 24-hour window instead
 * would put two incompatible definitions under one heading, and the reader has
 * no way to tell which is which.
 *
 * <p>The response is a chart payload, but only its {@code meta} block is read:
 * it already carries the last price and the previous close, so there is no
 * need to pull candles and derive them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YahooQuoteProvider {

    private static final String BASE =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&range=5d";

    /** What we ask for, and what each instrument is called in the UI. */
    private record Instrument(String key, String symbol, String label, String category) {
    }

    private static final List<Instrument> METALS = List.of(
            new Instrument("GOLD", "GC=F", "Gold", "METAL"),
            new Instrument("SILVER", "SI=F", "Silver", "METAL"));

    private static final List<Instrument> FX = List.of(
            new Instrument("USD_TND", "USDTND=X", "USD / TND", "FX"),
            new Instrument("EUR_TND", "EURTND=X", "EUR / TND", "FX"));

    private final PageFetcher pageFetcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Every instrument this provider covers, in one pass.
     *
     * <p>One call rather than separate metal and FX fetches, because the
     * caller caches the result and splits it. Two independent fetches meant
     * the macro banner and the commodities banner each queued their own
     * requests at a host that only tolerates one every few seconds — whichever
     * ran second came back rate-limited and its half of the UI went blank.
     *
     * <p>Crypto is not here: it moved to CoinGecko, both because this endpoint
     * would not serve seven instruments in a burst and because a 24/7 market
     * has no "previous close" to measure against.
     */
    public Mono<List<MarketQuoteDto>> fetchAllQuotes() {
        return fetchAll(Stream.concat(METALS.stream(), FX.stream()).toList());
    }

    /**
     * Sequential rather than parallel: {@link PageFetcher} paces per host, so
     * firing these concurrently would only queue them behind each other while
     * holding more threads. Seven quotes at a shared host is a few seconds.
     */
    private Mono<List<MarketQuoteDto>> fetchAll(List<Instrument> instruments) {
        return Flux.fromIterable(instruments)
                .concatMap(this::fetchOne)
                .collectList()
                .onErrorResume(e -> {
                    log.warn("Market quote fetch failed: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private Mono<MarketQuoteDto> fetchOne(Instrument instrument) {
        return Mono.fromCallable(() -> {
                    String body = pageFetcher.fetchData(String.format(BASE, instrument.symbol()));
                    if (body == null || body.isBlank()) {
                        return null;
                    }
                    return toQuote(instrument, objectMapper.readTree(body));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("Quote for {} failed: {}", instrument.symbol(), e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Reads the meta block. Returns null — so the instrument is simply absent
     * rather than shown as zero — whenever either price is missing, since a
     * change computed against a missing baseline is not a number worth
     * printing.
     */
    private MarketQuoteDto toQuote(Instrument instrument, JsonNode json) {
        JsonNode meta = json.path("chart").path("result").path(0).path("meta");
        if (meta.isMissingNode()) {
            log.debug("No meta block for {}", instrument.symbol());
            return null;
        }

        BigDecimal price = decimalOrNull(meta, "regularMarketPrice");
        BigDecimal previousClose = decimalOrNull(meta, "chartPreviousClose");
        if (price == null || previousClose == null || previousClose.signum() == 0) {
            log.debug("Incomplete quote for {}", instrument.symbol());
            return null;
        }

        BigDecimal changeValue = price.subtract(previousClose);
        BigDecimal changePct = changeValue
                .multiply(BigDecimal.valueOf(100))
                .divide(previousClose, 2, RoundingMode.HALF_UP);

        return MarketQuoteDto.builder()
                .key(instrument.key())
                .label(instrument.label())
                .symbol(instrument.symbol())
                // Metals and crypto are quoted to the cent; the dinar crosses
                // move in the fourth decimal, so they keep more of it.
                .price(price.setScale("FX".equals(instrument.category()) ? 4 : 2, RoundingMode.HALF_UP))
                .previousClose(previousClose.setScale(
                        "FX".equals(instrument.category()) ? 4 : 2, RoundingMode.HALF_UP))
                .changeValue(changeValue.setScale(
                        "FX".equals(instrument.category()) ? 4 : 2, RoundingMode.HALF_UP))
                .changePct(changePct)
                .currency(meta.path("currency").asText("USD"))
                .category(instrument.category())
                // These have a real daily close to measure against, unlike
                // the 24/7 crypto market — see MarketQuoteDto.changeBasis.
                .changeBasis("PREVIOUS_CLOSE")
                .fetchedAt(LocalDateTime.now())
                .build();
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? BigDecimal.valueOf(value.asDouble()) : null;
    }
}
