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

import org.springframework.data.domain.Limit;

import java.time.LocalDate;
import java.util.ArrayList;
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

    /**
     * @param bars how many trading days to feed the indicators, counted in
     *             bars rather than calendar days - see below
     */
    @GetMapping("/{symbol}/technical")
    public TechnicalAnalysisDto technical(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "250") int bars,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);

        // Bars, not a date window. The two are the same only for a stock that
        // trades every day; for a thinly traded one they are not close. UADH
        // holds 119 bars of which 9 fall inside 180 days, so the old window
        // returned too few rows to compute anything and every indicator came
        // back null - while the data needed to compute them sat in the table.
        var newestFirst = priceHistoryRepository.findBySymbolOrderByTradeDateDesc(
                symbol, Limit.of(Math.max(1, bars)));

        // The calculator walks forward through time.
        var ascending = new ArrayList<>(newestFirst);
        Collections.reverse(ascending);
        return technicalAnalysisCalculator.compute(ascending);
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
