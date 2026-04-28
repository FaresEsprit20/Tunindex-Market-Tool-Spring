package com.tunindex.market_tool.domain.services.stock;

import com.tunindex.market_tool.core.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.core.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.domain.dto.providers.investingcom.StockDto;

import java.util.List;

public interface StockService {

    // ========== BASIC CRUD WITH PAGINATION ==========

    PagedResponse<StockDto> findAllStocks(PaginationAndFilteringDto paginationDto);

    StockDto findBySymbol(String symbol);

    StockDto findBySymbolAndExchange(String symbol, String exchange);

    // ========== FILTERING WITH SPECIFICATIONS (USING JpaSpecificationExecutor) ==========

    PagedResponse<StockDto> filterStocks(PaginationAndFilteringDto paginationDto);

    // ========== VALUE-BASED FILTERS ==========

    PagedResponse<StockDto> findUndervaluedStocks(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findOvervaluedStocks(PaginationAndFilteringDto paginationDto);

    // ========== 52-WEEK FILTERS ==========

    PagedResponse<StockDto> findStocksNear52WeekLow(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findStocksNear52WeekHigh(PaginationAndFilteringDto paginationDto);

    // ========== GRAHAM FILTERS ==========

    PagedResponse<StockDto> findUndervaluedByGraham(PaginationAndFilteringDto paginationDto);

    // ========== INVESTMENT STRATEGIES ==========

    PagedResponse<StockDto> findValueInvestorFavorites(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findGrowthInvestorFavorites(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findIncomeInvestorFavorites(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findContrarianFavorites(PaginationAndFilteringDto paginationDto);
    PagedResponse<StockDto> findGrahamCriteriaStocks(PaginationAndFilteringDto paginationDto);

    // ========== SORTING & RANKING ==========

    PagedResponse<StockDto> findMostActive(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findLargestMarketCap(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findHighestDividendYield(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findBestMarginOfSafety(PaginationAndFilteringDto paginationDto);

    PagedResponse<StockDto> findLowestPERatio(PaginationAndFilteringDto paginationDto);

    // ========== STATISTICS (No pagination needed) ==========

    List<Object[]> countStocksBySector();

    List<Object[]> countStocksByOwnership();

    List<Object[]> averagePeRatioBySector();

    List<Object[]> averageDividendYieldBySector();

    // ========== ACTIONS ==========

    void refreshStockData(String symbol);
}