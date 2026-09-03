package com.tunindex.market_tool.collector.services.backfill;

import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.history.PriceHistoryService;
import com.tunindex.market_tool.collector.services.news.StockNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backfills the datasets that are otherwise populated lazily, one symbol at
 * a time, when a user opens that stock: daily price history and news.
 *
 * <p>That laziness left most of the exchange blank — technicals, charts and
 * the Tunindex Scorer's timing/momentum/news components all read these
 * tables, so a symbol nobody had visited scored on fundamentals alone.
 *
 * <p>Deliberately sequential with a pause between symbols. The source is a
 * small public site being scraped for ~70 symbols in a row; firing those
 * concurrently would be rude and is the kind of thing that gets a scraper
 * blocked. A full run therefore takes minutes, which is why it runs in the
 * background and reports progress rather than blocking a request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillServiceImpl implements BackfillService {

    /** Pause between symbols, so the run stays polite to the source site. */
    private static final long DELAY_BETWEEN_SYMBOLS_MS = 1500;
    private static final int NEWS_LIMIT = 20;

    private final StockRepository stockRepository;
    private final PriceHistoryService priceHistoryService;
    private final StockNewsService stockNewsService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger historyOk = new AtomicInteger();
    private final AtomicInteger newsOk = new AtomicInteger();
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicReference<String> currentSymbol = new AtomicReference<>("");
    private final AtomicReference<String> lastFinishedAt = new AtomicReference<>(null);

    @Override
    public boolean start(int historyDays, boolean includeNews) {
        if (!running.compareAndSet(false, true)) {
            log.info("⏭️ Backfill requested but a run is already in progress");
            return false;
        }

        processed.set(0);
        historyOk.set(0);
        newsOk.set(0);
        failures.set(0);
        currentSymbol.set("");

        Mono.fromRunnable(() -> runBackfill(historyDays, includeNews))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        return true;
    }

    private void runBackfill(int historyDays, boolean includeNews) {
        try {
            List<Stock> stocks = stockRepository.findAll();
            total.set(stocks.size());
            LocalDate from = LocalDate.now().minusDays(historyDays);
            LocalDate to = LocalDate.now();

            log.info("📥 Backfill starting for {} symbols (historyDays={}, includeNews={})",
                    stocks.size(), historyDays, includeNews);

            for (Stock stock : stocks) {
                String symbol = stock.getSymbol();
                currentSymbol.set(symbol);

                try {
                    var points = priceHistoryService.refreshAndGet(symbol, from, to).block();
                    if (points != null && !points.isEmpty()) {
                        historyOk.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                    log.warn("Backfill: history failed for {}: {}", symbol, e.getMessage());
                }

                if (includeNews) {
                    try {
                        var news = stockNewsService.getNews(symbol, NEWS_LIMIT).block();
                        if (news != null && !news.isEmpty()) {
                            newsOk.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        log.warn("Backfill: news failed for {}: {}", symbol, e.getMessage());
                    }
                }

                int done = processed.incrementAndGet();
                if (done % 10 == 0) {
                    log.info("📥 Backfill progress: {}/{} symbols", done, total.get());
                }

                try {
                    Thread.sleep(DELAY_BETWEEN_SYMBOLS_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Backfill interrupted after {} symbols", done);
                    break;
                }
            }

            log.info("✅ Backfill complete: {}/{} symbols — history for {}, news for {}, {} failures",
                    processed.get(), total.get(), historyOk.get(), newsOk.get(), failures.get());
        } catch (Exception e) {
            log.error("❌ Backfill run failed: {}", e.getMessage(), e);
        } finally {
            currentSymbol.set("");
            lastFinishedAt.set(java.time.LocalDateTime.now().toString());
            running.set(false);
        }
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", running.get());
        status.put("processed", processed.get());
        status.put("total", total.get());
        status.put("historyPopulated", historyOk.get());
        status.put("newsPopulated", newsOk.get());
        status.put("failures", failures.get());
        status.put("currentSymbol", currentSymbol.get());
        status.put("lastFinishedAt", lastFinishedAt.get());
        return status;
    }
}
