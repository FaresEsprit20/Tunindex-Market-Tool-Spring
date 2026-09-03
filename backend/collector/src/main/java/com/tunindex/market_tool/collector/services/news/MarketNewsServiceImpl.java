package com.tunindex.market_tool.collector.services.news;

import com.tunindex.market_tool.collector.dto.news.MarketNewsDto;
import com.tunindex.market_tool.collector.entities.MarketNews;
import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaMarketNewsProvider;
import com.tunindex.market_tool.collector.repository.jpa.MarketNewsRepository;
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
public class MarketNewsServiceImpl implements MarketNewsService {

    /** The exchange feed moves faster than the per-stock ones during a session. */
    private static final Duration FRESHNESS_WINDOW = Duration.ofMinutes(10);

    private final IlBoursaMarketNewsProvider provider;
    private final MarketNewsRepository repository;
    private final NewsSentimentClassifier sentimentClassifier;

    @Override
    public Mono<List<MarketNewsDto>> getMarketNews(int limit) {
        boolean isFresh = repository.findTopByOrderByScrapedAtDesc()
                .map(latest -> Duration.between(latest.getScrapedAt(), LocalDateTime.now())
                        .compareTo(FRESHNESS_WINDOW) < 0)
                .orElse(false);

        if (isFresh) {
            return Mono.fromCallable(() -> getStored(limit)).subscribeOn(Schedulers.boundedElastic());
        }

        return provider.fetchMarketNews()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(this::upsert)
                .then(Mono.fromCallable(() -> getStored(limit)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional(readOnly = true)
    protected List<MarketNewsDto> getStored(int limit) {
        return repository.findAllByOrderByPublishedAtDesc(Limit.of(limit))
                .stream()
                .map(MarketNewsDto::fromEntity)
                .toList();
    }

    // Not @Transactional: self-invoked from within the class, so Spring's
    // proxy wouldn't apply it anyway — same pattern as StockNewsServiceImpl.
    private void upsert(List<IlBoursaMarketNewsProvider.MarketNewsItem> items) {
        if (items.isEmpty()) {
            log.debug("No market news items returned");
            return;
        }

        int saved = 0;
        for (IlBoursaMarketNewsProvider.MarketNewsItem item : items) {
            if (repository.existsByUrl(item.url())) {
                continue;
            }
            // Sentiment is classified once at write time: the headline text
            // never changes, so re-deriving it on every read would be waste.
            String sentiment = sentimentClassifier.classify(item.headline()).sentiment().name();
            repository.save(MarketNews.builder()
                    .headline(item.headline())
                    .url(item.url())
                    .publishedAt(item.publishedAt())
                    .relatedPrice(item.relatedPrice())
                    .relatedChangePct(item.relatedChangePct())
                    .sentiment(sentiment)
                    .build());
            saved++;
        }
        log.info("📰 Upserted {} new market news items", saved);
    }
}
