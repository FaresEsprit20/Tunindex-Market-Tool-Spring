package com.tunindex.market_tool.collector.services.news;

import com.tunindex.market_tool.collector.dto.news.NewsImpactDto;
import com.tunindex.market_tool.collector.dto.news.StockNewsDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NewsImpactServiceImpl implements NewsImpactService {

    private final StockNewsService stockNewsService;
    private final NewsSentimentClassifier sentimentClassifier;
    private final PriceHistoryRepository priceHistoryRepository;

    @Override
    public Mono<List<NewsImpactDto>> getImpact(String symbol, int limit, int tradingDaysAfter) {
        return stockNewsService.getNews(symbol, limit)
                .publishOn(Schedulers.boundedElastic())
                .map(newsItems -> newsItems.stream()
                        .map(item -> toImpact(symbol, item, tradingDaysAfter))
                        .toList());
    }

    private NewsImpactDto toImpact(String symbol, StockNewsDto news, int tradingDaysAfter) {
        NewsSentimentClassifier.Classification classification = sentimentClassifier.classify(news.getHeadline());
        LocalDate publishDate = news.getPublishedAt().toLocalDate();

        Optional<PriceHistory> before = priceHistoryRepository
                .findBySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(symbol, publishDate, Limit.of(1))
                .stream().findFirst();

        List<PriceHistory> afterWindow = priceHistoryRepository
                .findBySymbolAndTradeDateGreaterThanOrderByTradeDateAsc(symbol, publishDate, Limit.of(tradingDaysAfter));
        Optional<PriceHistory> after = afterWindow.size() == tradingDaysAfter
                ? Optional.of(afterWindow.get(afterWindow.size() - 1))
                : Optional.empty();

        NewsImpactDto.NewsImpactDtoBuilder builder = NewsImpactDto.builder()
                .headline(news.getHeadline())
                .url(news.getUrl())
                .publishedAt(news.getPublishedAt())
                .sentiment(classification.sentiment().name())
                .matchedKeywords(classification.matchedKeywords());

        if (before.isPresent() && after.isPresent()
                && before.get().getClose() != null && after.get().getClose() != null
                && before.get().getClose().compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal beforeClose = before.get().getClose();
            BigDecimal afterClose = after.get().getClose();
            BigDecimal changePct = afterClose.subtract(beforeClose)
                    .divide(beforeClose, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            builder.priceBeforeDate(before.get().getTradeDate())
                    .priceBeforeClose(beforeClose)
                    .priceAfterDate(after.get().getTradeDate())
                    .priceAfterClose(afterClose)
                    .priceChangePct(changePct)
                    .priceMoveMatchesSentiment(matchesSentiment(classification.sentiment(), changePct));
        }

        return builder.build();
    }

    private Boolean matchesSentiment(NewsSentimentClassifier.Sentiment sentiment, BigDecimal changePct) {
        if (sentiment == NewsSentimentClassifier.Sentiment.NEUTRAL) {
            return null;
        }
        boolean priceRose = changePct.compareTo(BigDecimal.ZERO) > 0;
        boolean priceFell = changePct.compareTo(BigDecimal.ZERO) < 0;
        if (sentiment == NewsSentimentClassifier.Sentiment.POSITIVE) {
            return priceRose;
        }
        return priceFell;
    }
}
