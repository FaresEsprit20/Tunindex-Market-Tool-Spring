package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.investingcom.StockDto;
import com.tunindex.market_tool.collector.services.stock.StockService;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/stock-data")
@RequiredArgsConstructor
@Slf4j
public class StockDataController {

    private final StockService stockService;

    @GetMapping("/symbol/{symbol}")
    public StockDto getBySymbol(@PathVariable String symbol) {
        log.info("Collector: Getting stock by symbol: {}", symbol);
        return stockService.findBySymbol(symbol);
    }

    @GetMapping("/symbol/{symbol}/exchange/{exchange}")
    public StockDto getBySymbolAndExchange(@PathVariable String symbol, @PathVariable String exchange) {
        log.info("Collector: Getting stock by symbol: {} and exchange: {}", symbol, exchange);
        return stockService.findBySymbolAndExchange(symbol, exchange);
    }

    @PostMapping("/filter")
    public PagedResponse<StockDto> filterStocks(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("Collector: Filtering stocks");
        return stockService.filterStocks(paginationDto);
    }

    @GetMapping("/statistics/by-sector")
    public List<Object[]> countBySector() {
        log.info("Collector: Counting stocks by sector");
        return stockService.countStocksBySector();
    }

    @GetMapping("/statistics/by-ownership")
    public List<Object[]> countByOwnership() {
        log.info("Collector: Counting stocks by ownership");
        return stockService.countStocksByOwnership();
    }

    @PutMapping("/refresh/{symbol}")
    public void refreshStock(@PathVariable String symbol) {
        log.info("Collector: Refreshing stock: {}", symbol);
        stockService.refreshStockData(symbol);
    }
}