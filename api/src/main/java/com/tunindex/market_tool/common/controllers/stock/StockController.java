package com.tunindex.market_tool.common.controllers.stock;

import com.tunindex.market_tool.common.services.stock.StockService;
import com.tunindex.market_tool.common.dto.providers.investingcom.StockDto;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StockController implements StockApi {

    private final StockService stockService;

    @Override
    public StockDto findBySymbol(String symbol) {
        log.info("=== StockController.findBySymbol() called ===");
        log.info("Searching for stock with symbol: {}", symbol);

        // Add manual validation for empty symbol
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidEntityException(
                    "Symbol cannot be empty",
                    ErrorCodes.EMPTY_SYMBOL,
                    Collections.singletonList("Symbol parameter is required and cannot be empty")
            );
        }

        StockDto result = stockService.findBySymbol(symbol);
        log.info("Stock found - Symbol: {}, Name: {}, Exchange: {}",
                result.getSymbol(), result.getName(), result.getExchange());
        return result;
    }

    @Override
    public StockDto findBySymbolAndExchange(String symbol, String exchange) {
        log.info("=== StockController.findBySymbolAndExchange() called ===");
        log.info("Searching for stock with symbol: {} and exchange: {}", symbol, exchange);

        // Add manual validation for empty parameters
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidEntityException(
                    "Symbol cannot be empty",
                    ErrorCodes.EMPTY_SYMBOL,
                    Collections.singletonList("Symbol parameter is required and cannot be empty")
            );
        }
        if (exchange == null || exchange.trim().isEmpty()) {
            throw new InvalidEntityException(
                    "Exchange cannot be empty",
                    ErrorCodes.EMPTY_EXCHANGE,
                    Collections.singletonList("Exchange parameter is required and cannot be empty")
            );
        }

        StockDto result = stockService.findBySymbolAndExchange(symbol, exchange);
        log.info("Stock found - Symbol: {}, Name: {}, Exchange: {}",
                result.getSymbol(), result.getName(), result.getExchange());
        return result;
    }

    @Override
    public PagedResponse<StockDto> filterStocks(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.filterStocks() called ===");
        log.info("Filter parameters - page: {}, size: {}, filters: {}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        PagedResponse<StockDto> result = stockService.filterStocks(paginationDto);
        log.info("Found {} stocks matching filters out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public List<Object[]> countStocksBySector() {
        log.info("=== StockController.countStocksBySector() called ===");

        List<Object[]> result = stockService.countStocksBySector();
        log.info("Found statistics for {} sectors", result.size());
        return result;
    }

    @Override
    public List<Object[]> countStocksByOwnership() {
        log.info("=== StockController.countStocksByOwnership() called ===");

        List<Object[]> result = stockService.countStocksByOwnership();
        log.info("Found statistics for {} ownership types", result.size());
        return result;
    }

    @Override
    public void refreshStockData(String symbol) {
        log.info("=== StockController.refreshStockData() called ===");
        log.info("Refreshing stock data for symbol: {}", symbol);

        // Add manual validation for empty symbol
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidEntityException(
                    "Symbol cannot be empty",
                    ErrorCodes.EMPTY_SYMBOL,
                    Collections.singletonList("Symbol parameter is required and cannot be empty")
            );
        }

        stockService.refreshStockData(symbol);
        log.info("Stock data refresh initiated successfully for symbol: {}", symbol);
    }
}