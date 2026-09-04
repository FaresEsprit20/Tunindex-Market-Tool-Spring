package com.tunindex.market_tool.collector.services.macro;

import com.tunindex.market_tool.collector.dto.macro.MacroIndicatorDto;
import com.tunindex.market_tool.collector.dto.macro.MacroSnapshotDto;
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
 * <p>Cached for hours rather than fetched per request. These figures change
 * monthly at most — the policy rate has been unchanged for the whole period
 * we have data for — so hitting two external sites on every dashboard load
 * would be pure waste and, in the BCT's case, discourteous.
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

    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final BctRatesProvider bctRatesProvider;
    private final WorldBankProvider worldBankProvider;

    private volatile MacroSnapshotDto cached;

    public Mono<MacroSnapshotDto> snapshot() {
        MacroSnapshotDto current = cached;
        if (current != null && current.getFetchedAt() != null
                && current.getFetchedAt().isAfter(LocalDateTime.now().minus(CACHE_TTL))) {
            return Mono.just(current);
        }
        return refresh();
    }

    /**
     * Both sources are fetched together, and either may come back empty
     * without failing the other — a BCT outage should not also cost us the
     * inflation figure.
     */
    public Mono<MacroSnapshotDto> refresh() {
        Mono<List<MacroIndicatorDto>> rates = bctRatesProvider.fetchRates().defaultIfEmpty(List.of());
        Mono<List<MacroIndicatorDto>> economy = worldBankProvider.fetchEconomy().defaultIfEmpty(List.of());

        return Mono.zip(rates, economy)
                .map(both -> {
                    List<String> unavailable = new ArrayList<>();
                    if (both.getT1().isEmpty()) {
                        unavailable.add("Banque Centrale de Tunisie");
                    }
                    if (both.getT2().isEmpty()) {
                        unavailable.add("World Bank");
                    }

                    MacroSnapshotDto snapshot = MacroSnapshotDto.builder()
                            .rates(both.getT1())
                            .economy(both.getT2())
                            .fetchedAt(LocalDateTime.now())
                            .unavailable(unavailable)
                            .build();

                    // Only replace the cache when we got something; otherwise
                    // the previous good snapshot is more useful than an empty one.
                    if (!both.getT1().isEmpty() || !both.getT2().isEmpty()) {
                        cached = snapshot;
                    }
                    return cached != null ? cached : snapshot;
                })
                .onErrorResume(error -> {
                    log.warn("Macro refresh failed: {}", error.getMessage());
                    return cached != null ? Mono.just(cached) : Mono.just(MacroSnapshotDto.builder()
                            .rates(List.of())
                            .economy(List.of())
                            .unavailable(List.of("Banque Centrale de Tunisie", "World Bank"))
                            .build());
                });
    }

    /**
     * The policy rate, for anything that needs a real risk-free hurdle rather
     * than a configured guess — the Sharpe and Sortino ratios, principally.
     * Empty when we have never successfully read it, so callers fall back to
     * their configured default rather than silently assuming zero.
     */
    public Optional<BigDecimal> policyRatePct() {
        MacroSnapshotDto current = cached;
        if (current == null || current.getRates() == null) {
            return Optional.empty();
        }
        return current.getRates().stream()
                .filter(rate -> "POLICY_RATE".equals(rate.getKey()))
                .map(MacroIndicatorDto::getValue)
                .filter(java.util.Objects::nonNull)
                .findFirst();
    }
}
