package com.tunindex.market_tool.collector.services.stock;

import com.tunindex.market_tool.collector.dto.investingcom.StockDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;

import java.util.List;

public interface StockService {

    // ========== BASIC CRUD ==========
    StockDto findBySymbol(String symbol);
    StockDto findBySymbolAndExchange(String symbol, String exchange);

    // ========== FILTERING (Unified method with all specifications) ==========
    PagedResponse<StockDto> filterStocks(PaginationAndFilteringDto paginationDto);

    // ========== STATISTICS ==========
    List<Object[]> countStocksBySector();
    List<Object[]> countStocksByOwnership();

    // ========== ACTIONS ==========
    void refreshStockData(String symbol);
}