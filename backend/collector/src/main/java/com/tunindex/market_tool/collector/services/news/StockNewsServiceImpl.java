package com.tunindex.market_tool.collector.services.news;

import com.tunindex.market_tool.collector.dto.news.StockNewsDto;
import com.tunindex.market_tool.collector.entities.StockNews;
import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaNewsProvider;
import com.tunindex.market_tool.collector.repository.jpa.StockNewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockNewsServiceImpl implements StockNewsService {

    // News doesn't change minute-to-minute — re-scraping this often just
    // keeps headlines reasonably current without hammering ilboursa on
    // every stock-detail/analysis page view.
    private static final Duration FRESHNESS_WINDOW = Duration.ofMinutes(20);

    private final IlBoursaNewsProvider ilBoursaNewsProvider;
    private final StockNewsRepository stockNewsRepository;

    @Override
    public Mono<List<StockNewsDto>> getNews(String symbol, int limit) {
        boolean isFresh = stockNewsRepository.findTopBySymbolOrderByScrapedAtDesc(symbol)
                .map(latest -> Duration.between(latest.getScrapedAt(), LocalDateTime.now()).compareTo(FRESHNESS_WINDOW) < 0)
                .orElse(false);

        if (isFresh) {
            return Mono.fromCallable(() -> getStored(symbol, limit))
                    .subscribeOn(Schedulers.boundedElastic());
        }

        return ilBoursaNewsProvider.fetchNews(symbol)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(items -> upsert(symbol, items))
                .then(Mono.fromCallable(() -> getStored(symbol, limit)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional(readOnly = true)
    protected List<StockNewsDto> getStored(String symbol, int limit) {
        return stockNewsRepository.findBySymbolOrderByPublishedAtDesc(symbol, Limit.of(limit))
                .stream()
                .map(StockNewsDto::fromEntity)
                .toList();
    }

    // Not @Transactional: self-invoked via `this.` from within the same
    // class, which bypasses Spring's proxy-based AOP — same reasoning as
    // PriceHistoryServiceImpl.upsert. Each save() is transactional on its own.
    private void upsert(String symbol, List<IlBoursaNewsProvider.NewsItem> items) {
        if (items.isEmpty()) {
            log.debug("No news items returned for {}", symbol);
            return;
        }

        int saved = 0;
        for (IlBoursaNewsProvider.NewsItem item : items) {
            if (stockNewsRepository.existsBySymbolAndUrl(symbol, item.url())) {
                continue;
            }
            stockNewsRepository.save(StockNews.builder()
                    .symbol(symbol)
                    .headline(item.headline())
                    .url(item.url())
                    .publishedAt(item.publishedAt())
                    .build());
            saved++;
        }
        log.info("📰 Upserted {} new news items for {}", saved, symbol);

        // Nothing new to insert but we did fetch — still bump freshness by
        // touching the most recent row, so we don't re-scrape every request
        // when the feed genuinely hasn't published anything new.
        if (saved == 0) {
            stockNewsRepository.findTopBySymbolOrderByScrapedAtDesc(symbol)
                    .ifPresent(latest -> {
                        latest.setScrapedAt(LocalDateTime.now());
                        stockNewsRepository.save(latest);
                    });
        }
    }
}
