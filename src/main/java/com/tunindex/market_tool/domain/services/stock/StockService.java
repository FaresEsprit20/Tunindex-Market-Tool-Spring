package com.tunindex.market_tool.domain.services.stock;

import com.tunindex.market_tool.core.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.core.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.domain.dto.providers.investingcom.StockDto;

import java.util.List;

public interface StockService {

    // Basic CRUD operations
    PagedResponse<StockDto> findAllStocks(PaginationAndFilteringDto paginationDto);

    StockDto findBySymbol(String symbol);

    StockDto findBySymbolAndExchange(String symbol, String exchange);

    // Advanced filtering
    PagedResponse<StockDto> filterStocks(PaginationAndFilteringDto paginationDto);

    // Value-based filters
    PagedResponse<StockDto> findUndervaluedStocks(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findOvervaluedStocks(PaginationAndFilteringDto paginationDto);

    // 52-week filters
    PagedResponse<StockDto> findStocksNear52WeekLow(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findStocksNear52WeekHigh(PaginationAndFilteringDto paginationDto);

    // Graham filters
    PagedResponse<StockDto> findUndervaluedByGraham(PaginationAndFilteringDto paginationDto);

    // Investment strategies
    PagedResponse<StockDto> findValueInvestorFavorites(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findGrowthInvestorFavorites(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findIncomeInvestorFavorites(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findContrarianFavorites(PaginationAndFilteringDto paginationDto);

    // Search
    List<StockDto> searchByKeyword(String keyword);

    // Sorting & ranking
    PagedResponse<StockDto> findMostActive(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findLargestMarketCap(PaginationAndFilteringDto paginationDto);

    // Statistics
    List<Object[]> countStocksBySector();

    List<Object[]> countStocksByOwnership();

    List<Object[]> averagePeRatioBySector();

    List<Object[]> averageDividendYieldBySector();

    // Actions
    void refreshStockData(String symbol);
}