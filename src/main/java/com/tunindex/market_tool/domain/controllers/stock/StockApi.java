package com.tunindex.market_tool.domain.controllers.stock;

import com.tunindex.market_tool.core.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.core.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.domain.dto.providers.investingcom.StockDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.tunindex.market_tool.core.utils.constants.Constants.APP_ROOT;

@Tag(name = "Stocks", description = "API for stock market operations")
public interface StockApi {

    @PostMapping(value = APP_ROOT + "/stocks/all",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get all stocks", description = "Returns paginated list of all stocks")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stocks retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters")
    })
    PagedResponse<StockDto> findAllStocks(@RequestBody PaginationAndFilteringDto paginationDto);

    @GetMapping(value = APP_ROOT + "/stocks/symbol/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Find stock by symbol", description = "Search for a stock by its trading symbol")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock found"),
            @ApiResponse(responseCode = "404", description = "Stock not found with given symbol"),
            @ApiResponse(responseCode = "400", description = "Invalid symbol")
    })
    StockDto findBySymbol(@PathVariable("symbol") String symbol);

    @GetMapping(value = APP_ROOT + "/stocks/symbol/{symbol}/exchange/{exchange}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Find stock by symbol and exchange",
            description = "Search for a stock by its trading symbol and exchange")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock found"),
            @ApiResponse(responseCode = "404", description = "Stock not found"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters")
    })
    StockDto findBySymbolAndExchange(
            @PathVariable("symbol") String symbol,
            @PathVariable("exchange") String exchange);

    @PostMapping(value = APP_ROOT + "/stocks/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Filter stocks", description = "Filter stocks with multiple criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Filtered stocks retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters")
    })
    PagedResponse<StockDto> filterStocks(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/undervalued",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get undervalued stocks",
            description = "Returns stocks with positive margin of safety")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Undervalued stocks retrieved successfully")
    })
    PagedResponse<StockDto> findUndervaluedStocks(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/overvalued",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get overvalued stocks",
            description = "Returns stocks with negative margin of safety")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Overvalued stocks retrieved successfully")
    })
    PagedResponse<StockDto> findOvervaluedStocks(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/near-52-week-low",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get stocks near 52-week low",
            description = "Returns stocks trading within 10% of their 52-week low")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stocks near 52-week low retrieved successfully")
    })
    PagedResponse<StockDto> findStocksNear52WeekLow(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/near-52-week-high",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get stocks near 52-week high",
            description = "Returns stocks trading within 10% of their 52-week high")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stocks near 52-week high retrieved successfully")
    })
    PagedResponse<StockDto> findStocksNear52WeekHigh(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/graham-undervalued",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Graham undervalued stocks",
            description = "Returns stocks where price is below Graham fair value")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Graham undervalued stocks retrieved successfully")
    })
    PagedResponse<StockDto> findUndervaluedByGraham(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/value-investor-favorites",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get value investor favorites",
            description = "Returns stocks with MOS > 20%, profitable, and low debt")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Value investor favorites retrieved successfully")
    })
    PagedResponse<StockDto> findValueInvestorFavorites(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/growth-investor-favorites",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get growth investor favorites",
            description = "Returns stocks with high profit margin, reasonable PE, and positive MOS")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Growth investor favorites retrieved successfully")
    })
    PagedResponse<StockDto> findGrowthInvestorFavorites(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/income-investor-favorites",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get income investor favorites",
            description = "Returns stocks with high dividend yield, profitable, and low debt")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Income investor favorites retrieved successfully")
    })
    PagedResponse<StockDto> findIncomeInvestorFavorites(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/contrarian-favorites",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get contrarian favorites",
            description = "Returns stocks near 52-week low but profitable")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contrarian favorites retrieved successfully")
    })
    PagedResponse<StockDto> findContrarianFavorites(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/graham-criteria",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get Graham criteria stocks",
            description = "Returns stocks that meet Benjamin Graham's investment criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Graham criteria stocks retrieved successfully")
    })
    PagedResponse<StockDto> findGrahamCriteriaStocks(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/most-active",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get most active stocks",
            description = "Returns stocks sorted by trading volume")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Most active stocks retrieved successfully")
    })
    PagedResponse<StockDto> findMostActive(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/largest-market-cap",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get largest market cap stocks",
            description = "Returns stocks sorted by market capitalization descending")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Largest market cap stocks retrieved successfully")
    })
    PagedResponse<StockDto> findLargestMarketCap(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/highest-dividend-yield",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get highest dividend yield stocks",
            description = "Returns stocks with the highest dividend yields")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Highest dividend yield stocks retrieved successfully")
    })
    PagedResponse<StockDto> findHighestDividendYield(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/best-margin-of-safety",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get best margin of safety stocks",
            description = "Returns stocks with the highest margin of safety")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Best margin of safety stocks retrieved successfully")
    })
    PagedResponse<StockDto> findBestMarginOfSafety(@RequestBody PaginationAndFilteringDto paginationDto);

    @PostMapping(value = APP_ROOT + "/stocks/lowest-pe-ratio",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get lowest P/E ratio stocks",
            description = "Returns stocks with the lowest price-to-earnings ratios")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lowest P/E ratio stocks retrieved successfully")
    })
    PagedResponse<StockDto> findLowestPERatio(@RequestBody PaginationAndFilteringDto paginationDto);

    @GetMapping(value = APP_ROOT + "/stocks/statistics/by-sector",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Count stocks by sector",
            description = "Returns stock count grouped by sector")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    List<Object[]> countStocksBySector();

    @GetMapping(value = APP_ROOT + "/stocks/statistics/by-ownership",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Count stocks by ownership type",
            description = "Returns stock count grouped by ownership type")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    List<Object[]> countStocksByOwnership();

    @GetMapping(value = APP_ROOT + "/stocks/statistics/average-pe-by-sector",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Average P/E ratio by sector",
            description = "Returns average P/E ratio grouped by sector")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    List<Object[]> averagePeRatioBySector();

    @GetMapping(value = APP_ROOT + "/stocks/statistics/average-dividend-by-sector",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Average dividend yield by sector",
            description = "Returns average dividend yield grouped by sector")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    })
    List<Object[]> averageDividendYieldBySector();

    @PutMapping(value = APP_ROOT + "/stocks/refresh/{symbol}")
    @Operation(summary = "Refresh stock data",
            description = "Triggers a refresh of stock data for the given symbol")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock refresh initiated"),
            @ApiResponse(responseCode = "404", description = "Stock not found"),
            @ApiResponse(responseCode = "400", description = "Invalid symbol")
    })
    void refreshStockData(@PathVariable("symbol") String symbol);
}