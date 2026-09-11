package com.tunindex.market_tool.collector.services.macro;

import com.tunindex.market_tool.collector.dto.macro.MacroIndicatorDto;
import com.tunindex.market_tool.collector.dto.macro.MacroSnapshotDto;
import com.tunindex.market_tool.collector.dto.macro.MarketQuoteDto;
import com.tunindex.market_tool.collector.providers.yahoo.YahooQuoteProvider;
import com.tunindex.market_tool.collector.providers.coingecko.CoinGeckoProvider;
import com.tunindex.market_tool.collector.providers.bct.BctRatesProvider;
import com.tunindex.market_tool.collector.providers.worldbank.WorldBankProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The macro backdrop, combined from the central bank and the World Bank.
 *
 * <p>Cached rather than fetched per request: the rate and the national
 * accounts change monthly at most, so hitting these publishers on every
 * dashboard load would be waste and, in the central bank's case, discourteous.
 * The window is short enough that the live currency crosses in the same
 * payload stay honest.
 *
 * <p>A stale cache is served in preference to nothing when a source is
 * unreachable, with the fetch time attached so the client can show its age.
 * Dropping to an empty panel because a government website was briefly down
 * would be worse than showing last night's rate, which has not moved.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MacroIndicatorsService {

    /**
     * Shortened from six hours. That was right when this payload held only
     * annual statistics and a monthly rate; it now also carries live currency
     * crosses, and a six-hour-old exchange rate presented next to a "daily
     * change" would be wrong in a way the reader cannot see.
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    private final BctRatesProvider bctRatesProvider;
    private final WorldBankProvider worldBankProvider;
    private final YahooQuoteProvider yahooQuoteProvider;
    private final CoinGeckoProvider coinGeckoProvider;

    private volatile MacroSnapshotDto cached;

    /** Traded prices move continuously, so they get a shorter window. */
    private static final Duration QUOTES_TTL = Duration.ofMinutes(5);

    /**
     * One cache for every Yahoo instrument — metals and the dinar crosses.
     *
     * <p>Shared because both banners draw on it. When each fetched its own,
     * the two calls queued at a host that tolerates roughly one request every
     * six seconds, and whichever ran second was rate-limited into an empty
     * list. One pass, cached, serves both.
     */
    private volatile List<MarketQuoteDto> cachedYahooQuotes;
    private volatile LocalDateTime yahooFetchedAt;

    private volatile List<MarketQuoteDto> cachedCrypto;
    private volatile LocalDateTime cryptoFetchedAt;

    public Mono<MacroSnapshotDto> snapshot() {
        MacroSnapshotDto current = cached;
        if (current != null && current.getFetchedAt() != null
                && current.getFetchedAt().isAfter(LocalDateTime.now().minus(CACHE_TTL))) {
            return Mono.just(current);
        }
        return refresh();
    }

    /**
     * All three sources are fetched together, and any of them may come back
     * empty without failing the others — a central-bank outage should not
     * also cost us the inflation figure or the exchange rates.
     */
    public Mono<MacroSnapshotDto> refresh() {
        Mono<List<MacroIndicatorDto>> rates = bctRatesProvider.fetchRates().defaultIfEmpty(List.of());
        Mono<List<MacroIndicatorDto>> economy = worldBankProvider.fetchEconomy().defaultIfEmpty(List.of());
        Mono<List<MarketQuoteDto>> currencies = yahooQuotes()
                .map(quotes -> quotes.stream().filter(q -> "FX".equals(q.getCategory())).toList());

        return Mono.zip(rates, economy, currencies)
                .map(all -> {
                    List<String> unavailable = new ArrayList<>();
                    if (all.getT1().isEmpty()) {
                        unavailable.add("Banque Centrale de Tunisie");
                    }
                    if (all.getT2().isEmpty()) {
                        unavailable.add("World Bank");
                    }
                    if (all.getT3().isEmpty()) {
                        unavailable.add("Currency rates");
                    }

                    MacroSnapshotDto snapshot = MacroSnapshotDto.builder()
                            .rates(all.getT1())
                            .economy(all.getT2())
                            .currencies(all.getT3())
                            .fetchedAt(LocalDateTime.now())
                            .unavailable(unavailable)
                            .build();

                    // Only replace the cache when something came back; a
                    // previous good snapshot beats an empty one.
                    if (!all.getT1().isEmpty() || !all.getT2().isEmpty() || !all.getT3().isEmpty()) {
                        cached = snapshot;
                    }
                    return cached != null ? cached : snapshot;
                })
                .onErrorResume(error -> {
                    log.warn("Macro refresh failed: {}", error.getMessage());
                    return cached != null ? Mono.just(cached) : Mono.just(MacroSnapshotDto.builder()
                            .rates(List.of())
                            .economy(List.of())
                            .currencies(List.of())
                            .unavailable(List.of("Banque Centrale de Tunisie", "World Bank", "Currency rates"))
                            .build());
                });
    }

    /**
     * Metals and crypto for the commodities banner.
     *
     * <p>Cached separately from the macro snapshot, and on its own clock: it
     * moves continuously where those figures do not. The window is short
     * enough that "daily change" stays truthful, and long enough that a
     * dashboard open in several tabs does not re-request seven quotes from a
     * provider that returns 429 for a burst.
     */
    public Mono<List<MarketQuoteDto>> commodities() {
        return Mono.zip(
                        // Metals first, then the risk assets - gold, silver,
                        // the two technology funds and MicroStrategy sit on
                        // one strip because they answer the same question: what
                        // is happening outside the BVMT that a Tunisian
                        // investor is weighing their equities against.
                        yahooQuotes().map(quotes -> quotes.stream()
                                .filter(quote -> "METAL".equals(quote.getCategory())
                                        || "EQUITY".equals(quote.getCategory()))
                                .toList()),
                        crypto())
                .map(both -> {
                    List<MarketQuoteDto> quotes = new ArrayList<>(both.getT1());
                    quotes.addAll(both.getT2());
                    return quotes;
                });
    }

    /**
     * Yahoo's instruments, fetched at most once per window and shared by both
     * banners. A partial or empty result keeps the last good set rather than
     * blanking a banner.
     */
    private Mono<List<MarketQuoteDto>> yahooQuotes() {
        List<MarketQuoteDto> current = cachedYahooQuotes;
        if (current != null && !current.isEmpty() && yahooFetchedAt != null
                && yahooFetchedAt.isAfter(LocalDateTime.now().minus(QUOTES_TTL))) {
            return Mono.just(current);
        }
        return yahooQuoteProvider.fetchAllQuotes()
                .defaultIfEmpty(List.of())
                .map(quotes -> {
                    if (!quotes.isEmpty()) {
                        cachedYahooQuotes = quotes;
                        yahooFetchedAt = LocalDateTime.now();
                    }
                    return quotes.isEmpty() && cachedYahooQuotes != null ? cachedYahooQuotes : quotes;
                });
    }

    private Mono<List<MarketQuoteDto>> crypto() {
        List<MarketQuoteDto> current = cachedCrypto;
        if (current != null && !current.isEmpty() && cryptoFetchedAt != null
                && cryptoFetchedAt.isAfter(LocalDateTime.now().minus(QUOTES_TTL))) {
            return Mono.just(current);
        }
        return coinGeckoProvider.fetchCrypto()
                .defaultIfEmpty(List.of())
                .map(quotes -> {
                    if (!quotes.isEmpty()) {
                        cachedCrypto = quotes;
                        cryptoFetchedAt = LocalDateTime.now();
                    }
                    return quotes.isEmpty() && cachedCrypto != null ? cachedCrypto : quotes;
                });
    }

    /**
     * The risk-free hurdle for anything that needs a real one rather than a
     * configured guess — the Sharpe and Sortino ratios, principally.
     *
     * <p>Reads the TMM. This used to read the policy rate, but that series is
     * no longer collected; the two have been identical for the whole period we
     * have data for, and the TMM is the rate Tunisian lending is actually
     * indexed to, so it is the better proxy of the two anyway. Left as a
     * silent fallback, a stale key here would have quietly reverted every
     * ratio to the configured default.
     *
     * <p>Empty when we have never read it, so callers fall back deliberately
     * rather than assuming zero.
     */
    public Optional<BigDecimal> policyRatePct() {
        MacroSnapshotDto current = cached;
        if (current == null || current.getRates() == null) {
            return Optional.empty();
        }
        return current.getRates().stream()
                .filter(rate -> "TMM".equals(rate.getKey()))
                .map(MacroIndicatorDto::getValue)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }
}
