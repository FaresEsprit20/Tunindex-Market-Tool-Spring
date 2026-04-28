package com.tunindex.market_tool.domain.controllers.stock;

import com.tunindex.market_tool.core.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.core.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.domain.dto.providers.investingcom.StockDto;
import com.tunindex.market_tool.domain.services.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StockController implements StockApi {

    private final StockService stockService;

    @Override
    public PagedResponse<StockDto> findAllStocks(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findAllStocks() called ===");
        log.info("Pagination parameters - page: {}, size: {}, sortField: {}, sortDirection: {}",
                paginationDto.getPage(), paginationDto.getSize(),
                paginationDto.getSortField(), paginationDto.getSortDirection());
        log.info("Filters: {}", paginationDto.getFilters());

        PagedResponse<StockDto> result = stockService.findAllStocks(paginationDto);
        log.info("Found {} stocks out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public StockDto findBySymbol(String symbol) {
        log.info("=== StockController.findBySymbol() called ===");
        log.info("Searching for stock with symbol: {}", symbol);

        StockDto result = stockService.findBySymbol(symbol);
        log.info("Stock found - Symbol: {}, Name: {}, Exchange: {}",
                result.getSymbol(), result.getName(), result.getExchange());
        return result;
    }

    @Override
    public StockDto findBySymbolAndExchange(String symbol, String exchange) {
        log.info("=== StockController.findBySymbolAndExchange() called ===");
        log.info("Searching for stock with symbol: {} and exchange: {}", symbol, exchange);

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
    public PagedResponse<StockDto> findUndervaluedStocks(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findUndervaluedStocks() called ===");
        log.info("Looking for undervalued stocks (margin of safety > 0)");

        PagedResponse<StockDto> result = stockService.findUndervaluedStocks(paginationDto);
        log.info("Found {} undervalued stocks out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findOvervaluedStocks(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findOvervaluedStocks() called ===");
        log.info("Looking for overvalued stocks (margin of safety < 0)");

        PagedResponse<StockDto> result = stockService.findOvervaluedStocks(paginationDto);
        log.info("Found {} overvalued stocks out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findStocksNear52WeekLow(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findStocksNear52WeekLow() called ===");
        log.info("Looking for stocks near 52-week low");

        PagedResponse<StockDto> result = stockService.findStocksNear52WeekLow(paginationDto);
        log.info("Found {} stocks near 52-week low out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findStocksNear52WeekHigh(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findStocksNear52WeekHigh() called ===");
        log.info("Looking for stocks near 52-week high");

        PagedResponse<StockDto> result = stockService.findStocksNear52WeekHigh(paginationDto);
        log.info("Found {} stocks near 52-week high out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findUndervaluedByGraham(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findUndervaluedByGraham() called ===");
        log.info("Looking for stocks undervalued by Graham criteria");

        PagedResponse<StockDto> result = stockService.findUndervaluedByGraham(paginationDto);
        log.info("Found {} stocks undervalued by Graham criteria out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findValueInvestorFavorites(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findValueInvestorFavorites() called ===");
        log.info("Looking for value investor favorites (MOS > 20%, profitable, low debt)");

        PagedResponse<StockDto> result = stockService.findValueInvestorFavorites(paginationDto);
        log.info("Found {} value investor favorites out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findGrowthInvestorFavorites(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findGrowthInvestorFavorites() called ===");
        log.info("Looking for growth investor favorites (high profit margin, reasonable PE, positive MOS)");

        PagedResponse<StockDto> result = stockService.findGrowthInvestorFavorites(paginationDto);
        log.info("Found {} growth investor favorites out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findIncomeInvestorFavorites(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findIncomeInvestorFavorites() called ===");
        log.info("Looking for income investor favorites (high dividend yield, profitable, low debt)");

        PagedResponse<StockDto> result = stockService.findIncomeInvestorFavorites(paginationDto);
        log.info("Found {} income investor favorites out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findContrarianFavorites(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findContrarianFavorites() called ===");
        log.info("Looking for contrarian favorites (near 52-week low but profitable)");

        PagedResponse<StockDto> result = stockService.findContrarianFavorites(paginationDto);
        log.info("Found {} contrarian favorites out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findGrahamCriteriaStocks(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findGrahamCriteriaStocks() called ===");
        log.info("Looking for stocks meeting Graham criteria");

        PagedResponse<StockDto> result = stockService.findGrahamCriteriaStocks(paginationDto);
        log.info("Found {} stocks meeting Graham criteria out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findMostActive(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findMostActive() called ===");
        log.info("Looking for most active stocks by volume");

        PagedResponse<StockDto> result = stockService.findMostActive(paginationDto);
        log.info("Found {} most active stocks out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findLargestMarketCap(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findLargestMarketCap() called ===");
        log.info("Looking for largest market cap stocks");

        PagedResponse<StockDto> result = stockService.findLargestMarketCap(paginationDto);
        log.info("Found {} largest market cap stocks out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findHighestDividendYield(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findHighestDividendYield() called ===");
        log.info("Looking for highest dividend yield stocks");

        PagedResponse<StockDto> result = stockService.findHighestDividendYield(paginationDto);
        log.info("Found {} highest dividend yield stocks out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findBestMarginOfSafety(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findBestMarginOfSafety() called ===");
        log.info("Looking for best margin of safety stocks");

        PagedResponse<StockDto> result = stockService.findBestMarginOfSafety(paginationDto);
        log.info("Found {} best margin of safety stocks out of {} total",
                result.getContent().size(), result.getTotalElements());
        return result;
    }

    @Override
    public PagedResponse<StockDto> findLowestPERatio(@RequestBody PaginationAndFilteringDto paginationDto) {
        log.info("=== StockController.findLowestPERatio() called ===");
        log.info("Looking for lowest P/E ratio stocks");

        PagedResponse<StockDto> result = stockService.findLowestPERatio(paginationDto);
        log.info("Found {} lowest P/E ratio stocks out of {} total",
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
    public List<Object[]> averagePeRatioBySector() {
        log.info("=== StockController.averagePeRatioBySector() called ===");

        List<Object[]> result = stockService.averagePeRatioBySector();
        log.info("Calculated average P/E ratio for {} sectors", result.size());
        return result;
    }

    @Override
    public List<Object[]> averageDividendYieldBySector() {
        log.info("=== StockController.averageDividendYieldBySector() called ===");

        List<Object[]> result = stockService.averageDividendYieldBySector();
        log.info("Calculated average dividend yield for {} sectors", result.size());
        return result;
    }

    @Override
    public void refreshStockData(String symbol) {
        log.info("=== StockController.refreshStockData() called ===");
        log.info("Refreshing stock data for symbol: {}", symbol);

        stockService.refreshStockData(symbol);
        log.info("Stock data refresh initiated successfully for symbol: {}", symbol);
    }
}