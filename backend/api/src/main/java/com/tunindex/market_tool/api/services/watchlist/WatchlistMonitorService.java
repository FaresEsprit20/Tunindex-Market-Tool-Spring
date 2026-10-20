package com.tunindex.market_tool.api.services.watchlist;

import com.tunindex.market_tool.api.dto.stock.StockResponseDto;
import com.tunindex.market_tool.api.entities.WatchlistItem;
import com.tunindex.market_tool.api.repository.WatchlistItemRepository;
import com.tunindex.market_tool.api.services.notification.NotificationService;
import com.tunindex.market_tool.api.entities.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tells users when something on their watchlist actually moves.
 *
 * <p>Distinct from the alert engine, which fires on thresholds the user set
 * themselves. This fires on nothing being configured at all — putting a stock
 * on a watchlist is already a statement that you want to hear about it, and
 * requiring a second setup step to get any signal made the watchlist inert.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}: it makes a blocking
 * downstream call for quotes, and holding a pooled connection across that
 * round trip is what exhausted the Hikari pool elsewhere in this service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistMonitorService {

    private static final String COLLECTOR_URL = "http://collector-service/internal/stock-data";

    private final WatchlistItemRepository watchlistItemRepository;
    private final NotificationService notificationService;
    private final WebClient.Builder webClientBuilder;

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    /** Absolute day move, in percent, that counts as worth interrupting for. */
    @Value("${market-tool.watchlist.move-threshold-pct:3.0}")
    private BigDecimal moveThresholdPct;

    @Value("${market-tool.watchlist.monitor-enabled:true}")
    private boolean enabled;

    /**
     * One notification per user, per symbol, per day and direction.
     *
     * <p>Without this the job would re-notify on every pass for as long as a
     * stock stayed 3% down — which is most of a bad day, and is how a useful
     * signal turns into something the user mutes. The direction is part of
     * the key so a name that swings from down to up is still reported.
     */
    private final Map<String, LocalDate> alreadyNotified = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${market-tool.watchlist.monitor-interval-ms:600000}",
            initialDelayString = "${market-tool.watchlist.monitor-initial-delay-ms:60000}")
    public void checkWatchlists() {
        if (!enabled) {
            return;
        }

        List<WatchlistItem> items = watchlistItemRepository.findAllWithUser();
        if (items.isEmpty()) {
            return;
        }

        // One quote lookup for every watched symbol across all users, rather
        // than one per user per symbol — the same name usually appears on
        // several watchlists.
        List<String> symbols = items.stream()
                .map(WatchlistItem::getSymbol)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, StockResponseDto> quotes = fetchQuotes(symbols);
        if (quotes.isEmpty()) {
            return;
        }

        // Users come back already attached to their items by the fetch join.
        Map<User, List<WatchlistItem>> byUser = items.stream()
                .filter(item -> item.getUser() != null)
                .collect(Collectors.groupingBy(WatchlistItem::getUser,
                        LinkedHashMap::new, Collectors.toList()));

        int sent = 0;
        for (Map.Entry<User, List<WatchlistItem>> entry : byUser.entrySet()) {
            for (WatchlistItem item : entry.getValue()) {
                if (notifyIfMoved(entry.getKey(), item, quotes.get(item.getSymbol()))) {
                    sent++;
                }
            }
        }

        if (sent > 0) {
            log.info("Watchlist monitor: {} notification(s) across {} user(s)", sent, byUser.size());
        }
        pruneOldKeys();
    }

    private boolean notifyIfMoved(User user, WatchlistItem item, StockResponseDto stock) {
        if (stock == null || stock.getLastPrice() == null || stock.getPrevClose() == null
                || stock.getPrevClose().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        BigDecimal changePct = stock.getLastPrice()
                .subtract(stock.getPrevClose())
                .multiply(BigDecimal.valueOf(100))
                .divide(stock.getPrevClose(), 2, RoundingMode.HALF_UP);

        if (changePct.abs().compareTo(moveThresholdPct) < 0) {
            return false;
        }

        boolean rising = changePct.signum() > 0;
        String key = user.getId() + "|" + item.getSymbol() + "|" + (rising ? "UP" : "DOWN");
        LocalDate today = LocalDate.now();
        if (today.equals(alreadyNotified.get(key))) {
            return false;
        }
        alreadyNotified.put(key, today);

        notificationService.publish(
                user,
                item.getSymbol() + (rising ? " is up " : " is down ") + changePct.abs() + "%",
                (stock.getName() != null ? stock.getName() : item.getSymbol())
                        + " is trading at " + stock.getLastPrice() + " TND, "
                        + (rising ? "up from " : "down from ") + stock.getPrevClose()
                        + " at the previous close.",
                "WATCHLIST",
                rising ? "POSITIVE" : "NEGATIVE",
                item.getSymbol());
        return true;
    }

    private Map<String, StockResponseDto> fetchQuotes(List<String> symbols) {
        try {
            List<StockResponseDto> stocks = webClientBuilder.build()
                    .get()
                    .uri(COLLECTOR_URL + "/by-symbols?symbols={symbols}", String.join(",", symbols))
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<StockResponseDto>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (stocks == null) {
                return Map.of();
            }
            Map<String, StockResponseDto> bySymbol = new HashMap<>();
            for (StockResponseDto stock : stocks) {
                bySymbol.put(stock.getSymbol(), stock);
            }
            return bySymbol;
        } catch (RuntimeException ex) {
            log.warn("Watchlist monitor could not fetch quotes: {}", ex.getMessage());
            return Map.of();
        }
    }

    /** Keeps the dedupe map from growing without bound across trading days. */
    private void pruneOldKeys() {
        LocalDate today = LocalDate.now();
        List<String> stale = new ArrayList<>();
        alreadyNotified.forEach((key, date) -> {
            if (!today.equals(date)) {
                stale.add(key);
            }
        });
        stale.forEach(alreadyNotified::remove);
    }
}
