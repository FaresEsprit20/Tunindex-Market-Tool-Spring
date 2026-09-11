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
import com.tunindex.market_tool.collector.services.market.QuoteFreshness;
import com.tunindex.market_tool.collector.services.analysis.TechnicalAnalysisCalculator;
import com.tunindex.market_tool.collector.services.news.NewsSentimentClassifier;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Bars fed to the indicators. Comfortably more than the longest lookback
     * any of them uses (ADX needs about 40, SMA50 needs 50), with room to
     * spare so the readings settle rather than starting cold.
     */
    private static final int TECHNICAL_HISTORY_BARS = 250;
    private static final int NEWS_PER_SYMBOL = 15;

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final StockNewsRepository stockNewsRepository;
    private final TechnicalAnalysisCalculator technicalAnalysisCalculator;
    private final ReversalDetector reversalDetector;
    private final NewsSentimentClassifier newsSentimentClassifier;
    private final TunindexScorer scorer;

    @Override
    @Transactional(readOnly = true)
    public List<OpportunityScoreDto> findOpportunities(int limit, int minScore, boolean includeNews) {
        List<Stock> stocks = stockRepository.findAll();
        log.info("🏹 Scoring {} stocks for buy opportunities (minScore={})", stocks.size(), minScore);

        return stocks.stream()
                // Never recommend a name we cannot currently price. A symbol
                // whose exchange page has gone (delisted, renamed) keeps its
                // last known price forever, which makes it look permanently
                // cheap and permanently "at its 52-week low" — SIMPAR was
                // surfacing as a STRONG BUY on exactly that frozen data.
                // Scoring a specific symbol on request still works; this only
                // governs what we put in front of someone unprompted.
                .filter(QuoteFreshness::isFresh)
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

        OpportunityScoreDto score = scoreStock(stock, true);

        // An explicit request is still answered — the caller asked for this
        // symbol specifically — but a score computed on a price we can no
        // longer refresh has to say so, or it reads as a live assessment.
        if (!QuoteFreshness.isFresh(stock)) {
            Long hours = QuoteFreshness.hoursSinceQuote(stock);
            String age = hours == null
                    ? "We have never obtained a live quote for this symbol"
                    : "The last live quote was " + hours + " hours ago";
            score.getWarnings().add(age
                    + " — its exchange page could not be read, so every price-based"
                    + " figure below is frozen and this score is not current.");
        }
        return score;
    }

    private OpportunityScoreDto scoreStock(Stock stock, boolean includeNews) {
        List<PriceHistory> history = recentBars(stock.getSymbol());

        TechnicalAnalysisDto technical = computeTechnical(history, stock.getSymbol());
        List<NewsImpactDto> news = includeNews ? loadClassifiedNews(stock.getSymbol()) : List.of();
        // Whether the decline has actually ended, which the indicators alone
        // cannot say: an oversold reading is as common halfway down as it is
        // at the bottom.
        ReversalDetector.ReversalSignal reversal = reversalDetector.detect(history, technical);
        return scorer.score(stock, technical, news, oneYearReturnPct(stock.getSymbol()), reversal);
    }

    /**
     * The last {@value #TECHNICAL_HISTORY_BARS} trading days for a symbol.
     *
     * <p>Counted in bars, not calendar days. The old window asked for 180
     * days, which is the same thing only for a stock that trades daily. For a
     * thinly traded one it is not close: UADH holds 119 bars but just 9 of
     * them fall inside 180 days, so RSI, MACD, the moving averages, Bollinger,
     * Stochastic, Williams %R, ATR and ADX all came back null - and with no
     * technicals the timing and momentum halves of its score had nothing to
     * work from.
     *
     * <p>What this does not do is make a stale series fresh. UADH last traded
     * weeks ago, and its indicators now describe that day rather than today.
     * That is the honest reading of the data we have, and it is the caller's
     * job to say so - {@code scoreStock} already warns when the last quote is
     * old.
     */
    private List<PriceHistory> recentBars(String symbol) {
        List<PriceHistory> newestFirst = priceHistoryRepository
                .findBySymbolOrderByTradeDateDesc(symbol, Limit.of(TECHNICAL_HISTORY_BARS));

        // The indicators all walk forward through time.
        List<PriceHistory> ascending = new ArrayList<>(newestFirst);
        Collections.reverse(ascending);
        return ascending;
    }

    /**
     * Real 12-month return: first stored close on or after a year ago,
     * compared with the latest. The scraped oneYearReturn field is empty for
     * every tracked symbol, so this is what actually feeds momentum.
     */
    private BigDecimal oneYearReturnPct(String symbol) {
        List<PriceHistory> yearly = priceHistoryRepository
                .findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                        symbol, LocalDate.now().minusDays(365))
                .stream()
                .filter(p -> p.getClose() != null)
                .toList();

        // Two points is arithmetically enough but says nothing about a year;
        // require a real series before reporting a 12-month figure.
        if (yearly.size() < 20) {
            return null;
        }
        BigDecimal first = yearly.get(0).getClose();
        BigDecimal last = yearly.get(yearly.size() - 1).getClose();
        if (first.signum() == 0) {
            return null;
        }
        return last.subtract(first)
                .divide(first, 6, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private TechnicalAnalysisDto computeTechnical(List<PriceHistory> history, String symbol) {
        try {
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
