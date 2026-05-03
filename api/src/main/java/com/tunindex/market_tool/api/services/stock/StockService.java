package com.tunindex.market_tool.api.services.stock;

import com.tunindex.market_tool.api.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.api.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.api.dto.providers.investingcom.StockDto;

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