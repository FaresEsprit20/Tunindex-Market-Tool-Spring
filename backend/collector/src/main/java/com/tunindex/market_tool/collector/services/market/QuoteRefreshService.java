package com.tunindex.market_tool.collector.services.market;

import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaQuoteProvider;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps stored quotes current by re-reading the exchange page for every
 * tracked symbol on a schedule.
 *
 * <p>This exists because nothing else did. The full pipeline refreshes quotes
 * as a side effect of re-scraping fundamentals, but nothing ever triggered it,
 * so prices only moved when somebody pressed a button — and the app happily
 * presented day-old figures as "today's" gainers. This is the light path:
 * quote fields only, no fundamentals, so it can run every few minutes.
 *
 * <p>Symbols are fetched sequentially with a delay between them. The exchange
 * site is a small public server and we are not entitled to hammer it; a full
 * pass over ~69 names costs about a minute, which is well inside the interval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteRefreshService {

    /**
     * Politeness delay between symbol fetches. Sequential rather than
     * parallel for the same reason.
     */
    private static final long DELAY_BETWEEN_SYMBOLS_MS = 600;

    private final StockRepository stockRepository;
    private final IlBoursaQuoteProvider quoteProvider;
    private final MarketSessionService marketSessionService;

    @Value("${market-tool.quotes.refresh-enabled:true}")
    private boolean enabled;

    /**
     * How long to leave quotes alone once the market is shut.
     *
     * <p>Not "never": the closing auction prints after the continuous session
     * ends, so a pass that stopped at the bell would leave every stored close
     * one auction behind until the next morning. But once that print is in,
     * the numbers cannot change, and polling a small public site for figures
     * we know are static is both rude and pointless — hence hourly rather
     * than every five minutes.
     */
    @Value("${market-tool.quotes.closed-interval-minutes:60}")
    private long closedIntervalMinutes;

    /** Start of the last completed pass, for the closed-market throttle. */
    private volatile LocalDateTime lastPassAt;

    /**
     * Runs every five minutes. The exchange publishes continuously during the
     * session, so this is a staleness ceiling, not a tick feed — the UI shows
     * the actual quote age rather than implying real-time.
     */
    @Scheduled(fixedDelayString = "${market-tool.quotes.refresh-interval-ms:300000}",
            initialDelayString = "${market-tool.quotes.initial-delay-ms:30000}")
    public void refreshAllQuotes() {
        if (!enabled) {
            return;
        }

        if (!isSessionActive() && !closedRefreshDue()) {
            log.debug("Skipping quote refresh — market closed and quotes already current");
            return;
        }

        refreshNow();
    }

    /**
     * Runs a pass regardless of the session state. This is what a human
     * pressing "refresh" gets: the schedule's throttles exist to be polite to
     * the exchange site, not to refuse an explicit request.
     */
    public void refreshNow() {
        lastPassAt = LocalDateTime.now();
        List<String> symbols = trackedSymbols();
        if (symbols.isEmpty()) {
            return;
        }

        AtomicInteger updated = new AtomicInteger();
        List<String> unreachable = new ArrayList<>();

        for (String symbol : symbols) {
            try {
                IlBoursaQuoteProvider.LiveQuote quote = quoteProvider.fetchQuote(symbol)
                        .block(Duration.ofSeconds(25));

                if (quote == null || quote.lastPrice() == null) {
                    // The provider swallows errors into an empty Mono, so a
                    // 404 for a delisted or renamed symbol arrives here as a
                    // null. We record it rather than writing anything: the
                    // stored row keeps its old values AND its old
                    // liveQuoteAt, which is what marks it stale downstream.
                    unreachable.add(symbol);
                    continue;
                }

                applyQuote(symbol, quote);
                updated.incrementAndGet();
            } catch (RuntimeException ex) {
                unreachable.add(symbol);
                log.debug("Quote refresh failed for {}: {}", symbol, ex.getMessage());
            }

            sleepBetweenSymbols();
        }

        if (unreachable.isEmpty()) {
            log.info("Quote refresh complete: {}/{} symbols updated", updated.get(), symbols.size());
        } else {
            log.warn("Quote refresh complete: {}/{} updated; no live quote for {}",
                    updated.get(), symbols.size(), unreachable);
        }
    }

    /** Whether enough time has passed to justify a pass while the market is shut. */
    private boolean closedRefreshDue() {
        return lastPassAt == null
                || lastPassAt.isBefore(LocalDateTime.now().minusMinutes(closedIntervalMinutes));
    }

    /**
     * Symbols whose exchange page could not be read on the last full pass.
     * Exposed so an operator can see which names are going stale instead of
     * discovering it from a wrong figure in the UI.
     */
    @Transactional(readOnly = true)
    public List<String> staleSymbols(Duration olderThan) {
        LocalDateTime cutoff = LocalDateTime.now().minus(olderThan);
        return stockRepository.findAll().stream()
                .filter(stock -> stock.getPriceData() == null
                        || stock.getPriceData().getLiveQuoteAt() == null
                        || stock.getPriceData().getLiveQuoteAt().isBefore(cutoff))
                .map(Stock::getSymbol)
                .toList();
    }

    private List<String> trackedSymbols() {
        return stockRepository.findAll().stream().map(Stock::getSymbol).toList();
    }

    /**
     * One read-modify-write per symbol, run only after the network call has
     * already returned — holding a pooled connection across a blocking HTTP
     * round trip is what exhausted the Hikari pool elsewhere in this codebase.
     *
     * <p>Not annotated {@code @Transactional}: this is called from
     * {@link #refreshAllQuotes()} on the same instance, so Spring's proxy
     * would not intercept it and the annotation would be silently inert. The
     * repository's own transaction around {@code save} is what we actually
     * rely on, and one symbol per transaction is the granularity we want
     * anyway — a single unreachable name should not roll back the whole pass.
     */
    private void applyQuote(String symbol, IlBoursaQuoteProvider.LiveQuote quote) {
        stockRepository.findBySymbol(symbol).ifPresent(stock -> {
            if (stock.getPriceData() == null) {
                return;
            }
            stock.getPriceData().setLastPrice(quote.lastPrice());
            if (quote.prevClose() != null) {
                stock.getPriceData().setPrevClose(quote.prevClose());
            }
            if (quote.dayHigh() != null) {
                stock.getPriceData().setDayHigh(quote.dayHigh());
            }
            if (quote.dayLow() != null) {
                stock.getPriceData().setDayLow(quote.dayLow());
            }
            if (stock.getVolumeData() != null && quote.volume() != null) {
                stock.getVolumeData().setVolume(quote.volume());
            }
            stock.getPriceData().setLiveQuoteAt(LocalDateTime.now());
            stock.setLastUpdate(LocalDateTime.now());
            stockRepository.save(stock);
        });
    }

    /**
     * Trading hours plus a tail. The tail matters: the closing auction prints
     * after the continuous session ends, and stopping exactly at the bell
     * would leave every stored close one auction behind for the rest of the
     * day.
     */
    private boolean isSessionActive() {
        String state = marketSessionService.currentSession().getState();
        return "OPEN".equals(state) || "PRE_OPEN".equals(state) || "PRE_CLOSE".equals(state);
    }

    private void sleepBetweenSymbols() {
        try {
            Thread.sleep(DELAY_BETWEEN_SYMBOLS_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
