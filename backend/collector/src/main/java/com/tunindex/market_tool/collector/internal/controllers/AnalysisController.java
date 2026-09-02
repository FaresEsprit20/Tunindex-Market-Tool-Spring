package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.analysis.FundamentalAnalysisDto;
import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.analysis.FundamentalAnalysisCalculator;
import com.tunindex.market_tool.collector.services.analysis.TechnicalAnalysisCalculator;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;

@RestController
@RequestMapping("/internal/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisController {

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TechnicalAnalysisCalculator technicalAnalysisCalculator;
    private final FundamentalAnalysisCalculator fundamentalAnalysisCalculator;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping("/{symbol}/technical")
    public TechnicalAnalysisDto technical(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "180") int days,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        var history = priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                symbol, LocalDate.now().minusDays(days));
        return technicalAnalysisCalculator.compute(history);
    }

    @GetMapping("/{symbol}/fundamental")
    public FundamentalAnalysisDto fundamental(
            @PathVariable String symbol,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        Stock stock = stockRepository.findBySymbol(symbol)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Stock not found with symbol: " + symbol, ErrorCodes.STOCK_NOT_FOUND,
                        Collections.singletonList("symbol: " + symbol)));
        return fundamentalAnalysisCalculator.compute(stock);
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("❌ Invalid or missing API key for internal analysis call");
            throw new SecurityException("Invalid or missing API key");
        }
    }
}
