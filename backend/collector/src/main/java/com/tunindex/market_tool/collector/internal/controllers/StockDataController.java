package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.investingcom.StockDto;
import com.tunindex.market_tool.collector.services.stock.StockService;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/internal/stock-data")
@RequiredArgsConstructor
@Slf4j
public class StockDataController {

    private final StockService stockService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping("/symbol/{symbol}")
    public StockDto getBySymbol(
            @PathVariable String symbol,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        log.info("Collector: Getting stock by symbol: {}", symbol);
        return stockService.findBySymbol(symbol);
    }

    @GetMapping("/by-symbols")
    public List<StockDto> getBySymbols(
            @RequestParam String symbols,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        log.info("Collector: Batch stock lookup for: {}", symbols);
        return stockService.findBySymbols(Arrays.asList(symbols.split(",")));
    }

    @GetMapping("/symbol/{symbol}/exchange/{exchange}")
    public StockDto getBySymbolAndExchange(
            @PathVariable String symbol,
            @PathVariable String exchange,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        log.info("Collector: Getting stock by symbol: {} and exchange: {}", symbol, exchange);
        return stockService.findBySymbolAndExchange(symbol, exchange);
    }

    @PostMapping("/filter")
    public PagedResponse<StockDto> filterStocks(
            @RequestBody PaginationAndFilteringDto paginationDto,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        log.info("Collector: Filtering stocks");
        return stockService.filterStocks(paginationDto);
    }

    @GetMapping("/statistics/by-sector")
    public List<Object[]> countBySector(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        log.info("Collector: Counting stocks by sector");
        return stockService.countStocksBySector();
    }

    @GetMapping("/statistics/by-ownership")
    public List<Object[]> countByOwnership(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        log.info("Collector: Counting stocks by ownership");
        return stockService.countStocksByOwnership();
    }

    @PutMapping("/refresh/{symbol}")
    public void refreshStock(
            @PathVariable String symbol,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        log.info("Collector: Refreshing stock: {}", symbol);
        stockService.refreshStockData(symbol);
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("❌ Invalid or missing API key for internal call. Received: {}", apiKey);
            throw new SecurityException("Invalid or missing API key");
        }
    }
}