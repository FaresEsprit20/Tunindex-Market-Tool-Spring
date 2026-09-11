package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.dto.analyst.TradeSetupDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.analysis.TechnicalAnalysisCalculator;
import com.tunindex.market_tool.collector.services.analyst.TradifyAnalyst;
import com.tunindex.market_tool.collector.services.scoring.ReversalDetector;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The entry levels for one stock.
 *
 * <p>Separate from {@code /analysis/{symbol}/technical}, which reports the
 * indicators as they stand. This reports what to do about them: the band worth
 * buying in, where the idea fails, and what it is worth if it works.
 */
@RestController
@RequestMapping("/internal/analyst")
@RequiredArgsConstructor
@Slf4j
public class AnalystController {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TechnicalAnalysisCalculator technicalAnalysisCalculator;
    private final ReversalDetector reversalDetector;
    private final TradifyAnalyst tradifyAnalyst;

    /** Matches the scorer, so the page and the ranking cannot disagree. */
    private static final int HISTORY_BARS = 250;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping("/{symbol}/setup")
    public TradeSetupDto setup(@PathVariable String symbol,
                               @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);

        Stock stock = stockRepository.findBySymbol(symbol.trim().toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Stock not found with symbol: " + symbol,
                        ErrorCodes.STOCK_NOT_FOUND,
                        List.of("symbol: " + symbol)));

        // Bars, not a date window - see PriceHistoryRepository for why that
        // distinction decides whether a thinly traded name gets any levels.
        List<PriceHistory> newestFirst = priceHistoryRepository
                .findBySymbolOrderByTradeDateDesc(stock.getSymbol(), Limit.of(HISTORY_BARS));
        List<PriceHistory> history = new ArrayList<>(newestFirst);
        Collections.reverse(history);

        TechnicalAnalysisDto technical = technicalAnalysisCalculator.compute(history);
        ReversalDetector.ReversalSignal reversal = reversalDetector.detect(history, technical);
        return tradifyAnalyst.analyse(stock, technical, history, reversal);
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("Invalid or missing API key for internal analyst call");
            throw new SecurityException("Invalid or missing API key");
        }
    }
}
