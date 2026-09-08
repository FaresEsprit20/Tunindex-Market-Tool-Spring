package com.tunindex.market_tool.collector.providers.coingecko;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.collector.dto.macro.MarketQuoteDto;
import com.tunindex.market_tool.collector.services.scraping.PageFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Crypto prices, with the rolling 24-hour change that continuous markets use.
 *
 * <p>Crypto moved off the Yahoo provider for two reasons. The practical one:
 * Yahoo rate-limits a burst, and asking it for seven instruments meant the
 * last few came back empty — moving these three here leaves it four, which it
 * serves comfortably. The correctness one: Yahoo reports change against a
 * "previous close", and a 24/7 market has no close. A rolling window is what
 * every crypto venue quotes and what a reader expects, so the figure is both
 * more available and more right.
 *
 * <p>One request covers all three coins, which is also why this does not need
 * the careful pacing Yahoo does.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinGeckoProvider {

    private static final String URL = "https://api.coingecko.com/api/v3/simple/price"
            + "?ids=bitcoin,ethereum,solana&vs_currencies=usd&include_24hr_change=true";

    private record Coin(String key, String id, String label) {
    }

    private static final List<Coin> COINS = List.of(
            new Coin("BTC", "bitcoin", "Bitcoin"),
            new Coin("ETH", "ethereum", "Ethereum"),
            new Coin("SOL", "solana", "Solana"));

    private final PageFetcher pageFetcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<List<MarketQuoteDto>> fetchCrypto() {
        return Mono.fromCallable(() -> {
                    String body = pageFetcher.fetchData(URL);
                    if (body == null || body.isBlank()) {
                        return List.<MarketQuoteDto>of();
                    }
                    return parse(objectMapper.readTree(body));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("CoinGecko fetch failed: {}", e.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<MarketQuoteDto> parse(JsonNode json) {
        List<MarketQuoteDto> quotes = new ArrayList<>();

        for (Coin coin : COINS) {
            JsonNode node = json.path(coin.id());
            JsonNode price = node.path("usd");
            JsonNode change = node.path("usd_24h_change");

            if (!price.isNumber()) {
                log.debug("No price for {}", coin.id());
                continue;
            }

            BigDecimal last = BigDecimal.valueOf(price.asDouble()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal changePct = change.isNumber()
                    ? BigDecimal.valueOf(change.asDouble()).setScale(2, RoundingMode.HALF_UP)
                    : null;

            // Derived from the price and the percentage, because the API gives
            // the change but not the level it started from.
            BigDecimal previous = null;
            BigDecimal changeValue = null;
            if (changePct != null && changePct.compareTo(BigDecimal.valueOf(-100)) > 0) {
                previous = last.divide(
                        BigDecimal.ONE.add(changePct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)),
                        2, RoundingMode.HALF_UP);
                changeValue = last.subtract(previous);
            }

            quotes.add(MarketQuoteDto.builder()
                    .key(coin.key())
                    .label(coin.label())
                    .symbol(coin.id())
                    .price(last)
                    .previousClose(previous)
                    .changeValue(changeValue)
                    .changePct(changePct)
                    .currency("USD")
                    .category("CRYPTO")
                    .changeBasis("ROLLING_24H")
                    .fetchedAt(LocalDateTime.now())
                    .build());
        }

        if (quotes.isEmpty()) {
            log.warn("CoinGecko responded but no coin could be read — the payload shape may have changed");
        }
        return quotes;
    }
}
