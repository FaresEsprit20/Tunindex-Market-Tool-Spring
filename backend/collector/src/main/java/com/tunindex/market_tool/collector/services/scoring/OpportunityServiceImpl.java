package com.tunindex.market_tool.collector.services.scoring;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.dto.news.NewsImpactDto;
import com.tunindex.market_tool.collector.dto.scoring.OpportunityScoreDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.entities.StockNews;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockNewsRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.analysis.TechnicalAnalysisCalculator;
import com.tunindex.market_tool.collector.services.news.NewsSentimentClassifier;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks every tracked stock by its Tunindex Score (see {@link TunindexScorer}).
 *
 * <p>Deliberately reads only what is already stored: fundamentals from the
 * stocks table, technicals recomputed from stored price history, and
 * headlines already scraped into stock_news. It never triggers a live
 * scrape — scoring 69 symbols behind a live fetch each would take minutes
 * and hammer the source site, and yesterday's cached headline is the right
 * input for a ranking anyway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpportunityServiceImpl implements OpportunityService {

    private static final int TECHNICAL_HISTORY_DAYS = 180;
    private static final int NEWS_PER_SYMBOL = 15;

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final StockNewsRepository stockNewsRepository;
    private final TechnicalAnalysisCalculator technicalAnalysisCalculator;
    private final NewsSentimentClassifier newsSentimentClassifier;
    private final TunindexScorer scorer;

    @Override
    @Transactional(readOnly = true)
    public List<OpportunityScoreDto> findOpportunities(int limit, int minScore, boolean includeNews) {
        List<Stock> stocks = stockRepository.findAll();
        log.info("🏹 Scoring {} stocks for buy opportunities (minScore={})", stocks.size(), minScore);

        return stocks.stream()
                .map(stock -> scoreStock(stock, includeNews))
                .filter(score -> score.getOverallScore() >= minScore)
                .sorted(Comparator
                        .comparingInt(OpportunityScoreDto::getOverallScore).reversed()
                        // Break ties toward the stock we know more about, so a
                        // thinly-covered symbol never outranks a fully-covered
                        // one on an equal blended score.
                        .thenComparing(Comparator.comparingInt(OpportunityScoreDto::getDataCompleteness).reversed())
                        .thenComparing(OpportunityScoreDto::getSymbol))
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OpportunityScoreDto scoreSymbol(String symbol) {
        Stock stock = stockRepository.findBySymbol(symbol.trim().toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Stock not found with symbol: " + symbol,
                        ErrorCodes.STOCK_NOT_FOUND,
                        List.of("symbol: " + symbol)));
        return scoreStock(stock, true);
    }

    private OpportunityScoreDto scoreStock(Stock stock, boolean includeNews) {
        TechnicalAnalysisDto technical = computeTechnical(stock.getSymbol());
        List<NewsImpactDto> news = includeNews ? loadClassifiedNews(stock.getSymbol()) : List.of();
        return scorer.score(stock, technical, news);
    }

    private TechnicalAnalysisDto computeTechnical(String symbol) {
        try {
            List<PriceHistory> history = priceHistoryRepository
                    .findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                            symbol, LocalDate.now().minusDays(TECHNICAL_HISTORY_DAYS));
            if (history.isEmpty()) {
                return null;
            }
            return technicalAnalysisCalculator.compute(history);
        } catch (Exception e) {
            // One symbol's missing/odd history must not sink the whole ranking.
            log.warn("Technical analysis unavailable for {} while scoring: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Cached headlines with the same rule-based sentiment tag the news panel
     * shows. Price-impact fields are left null here: the scorer only reads
     * sentiment and date, and filling them would mean a price lookup per
     * headline per stock for no gain.
     */
    private List<NewsImpactDto> loadClassifiedNews(String symbol) {
        List<StockNews> stored = stockNewsRepository.findBySymbolOrderByPublishedAtDesc(
                symbol, Limit.of(NEWS_PER_SYMBOL));

        return stored.stream()
                .map(item -> {
                    NewsSentimentClassifier.Classification classification =
                            newsSentimentClassifier.classify(item.getHeadline());
                    return NewsImpactDto.builder()
                            .headline(item.getHeadline())
                            .url(item.getUrl())
                            .publishedAt(item.getPublishedAt())
                            .sentiment(classification.sentiment().name())
                            .matchedKeywords(classification.matchedKeywords())
                            .build();
                })
                .toList();
    }
}
