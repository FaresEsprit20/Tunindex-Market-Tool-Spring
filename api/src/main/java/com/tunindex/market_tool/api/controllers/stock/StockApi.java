package com.tunindex.market_tool.api.controllers.stock;

import com.tunindex.market_tool.common.dto.providers.investingcom.StockDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;


@Tag(name = "Stocks", description = "API for stock market operations")
public interface StockApi {

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
    @Operation(
            summary = "Filter stocks with advanced criteria",
            description = """
                    Filter stocks using any combination of criteria. All filters are optional and can be combined.
                    
                    ## Available Filters:
                    
                    ### Basic Information:
                    - `symbol`: String - Filter by symbol (partial match, case-insensitive)
                    - `name`: String - Filter by company name (partial match, case-insensitive)
                    - `exchange`: String - Filter by exchange (exact match, e.g., "NYSE", "NASDAQ")
                    - `sector`: String - Filter by sector (values: TECHNOLOGY, FINANCIAL, HEALTHCARE, ENERGY, CONSUMER_CYCLICAL, etc.)
                    - `ownershipType`: String - Filter by ownership (values: PUBLIC, PRIVATE, GOVERNMENT, FOREIGN)
                    
                    ### Price Filters:
                    - `minPrice`: BigDecimal - Minimum price per share
                    - `maxPrice`: BigDecimal - Maximum price per share
                    
                    ### 52-Week Range Filters:
                    - `minCloseTo52WeekLow`: BigDecimal - Minimum percentage above 52-week low (0-100)
                    - `maxCloseTo52WeekLow`: BigDecimal - Maximum percentage above 52-week low (0-100)
                    - `near52WeekLow`: BigDecimal - Stocks within X% of 52-week low (e.g., 10 for 10%)
                    - `near52WeekHigh`: BigDecimal - Stocks within X% of 52-week high (e.g., 90 for 90%)
                    
                    ### Profitability Filters:
                    - `minProfitMargin`: BigDecimal - Minimum profit margin percentage (e.g., 15 for 15%)
                    - `maxProfitMargin`: BigDecimal - Maximum profit margin percentage
                    - `profitable`: Boolean - True for positive EPS
                    
                    ### Margin of Safety Filters:
                    - `minMarginOfSafety`: BigDecimal - Minimum margin of safety percentage
                    - `maxMarginOfSafety`: BigDecimal - Maximum margin of safety percentage
                    - `undervalued`: Boolean - True for positive margin of safety
                    - `overvalued`: Boolean - True for negative margin of safety
                    
                    ### Graham Value Filters:
                    - `minGrahamFairValue`: BigDecimal - Minimum Graham fair value
                    - `maxGrahamFairValue`: BigDecimal - Maximum Graham fair value
                    - `priceBelowGrahamValue`: Boolean - True if price < Graham fair value
                    - `priceAboveGrahamValue`: Boolean - True if price > Graham fair value
                    
                    ### Debt Filters:
                    - `minDebtToEquity`: BigDecimal - Minimum debt-to-equity ratio
                    - `maxDebtToEquity`: BigDecimal - Maximum debt-to-equity ratio
                    - `lowDebt`: Boolean - True for debt-to-equity < 0.5
                    - `highDebt`: Boolean - True for debt-to-equity > 1.0
                    
                    ### EPS & BVPS Filters:
                    - `minEps`: BigDecimal - Minimum earnings per share
                    - `maxEps`: BigDecimal - Maximum earnings per share
                    - `minBvps`: BigDecimal - Minimum book value per share
                    - `maxBvps`: BigDecimal - Maximum book value per share
                    
                    ### Valuation Ratios:
                    - `minPeRatio`: BigDecimal - Minimum P/E ratio
                    - `maxPeRatio`: BigDecimal - Maximum P/E ratio
                    - `lowPeRatio`: Boolean - True for P/E ratio < 15
                    
                    ### Dividend Filters:
                    - `minDividendYield`: BigDecimal - Minimum dividend yield percentage
                    - `maxDividendYield`: BigDecimal - Maximum dividend yield percentage
                    - `highDividend`: Boolean - True for dividend yield > 4%
                    
                    ### Market Filters:
                    - `minMarketCap`: BigDecimal - Minimum market cap in dollars
                    - `maxMarketCap`: BigDecimal - Maximum market cap in dollars
                    - `minVolume`: Long - Minimum trading volume
                    
                    ### Date Filters:
                    - `updatedAfter`: String - Updated after date (ISO format: YYYY-MM-DDTHH:MM:SS)
                    - `updatedBefore`: String - Updated before date (ISO format: YYYY-MM-DDTHH:MM:SS)
                    
                    ### Combined Strategies (use these instead of individual filters):
                    - `valueInvestorFavorites`: Boolean - MOS > 20%, profitable, low debt
                    - `growthInvestorFavorites`: Boolean - Profit margin > 15%, PE < 25, positive MOS
                    - `incomeInvestorFavorites`: Boolean - Dividend yield > 4%, profitable, low debt
                    - `contrarianFavorites`: Boolean - Near 52-week low but profitable
                    - `grahamCriteria`: Boolean - Price below Graham value, low PE, low debt
                    """)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Filtered stocks retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters")
    })
    PagedResponse<StockDto> filterStocks(@RequestBody PaginationAndFilteringDto paginationDto);

    @GetMapping(value = APP_ROOT + "/stocks/statistics/by-sector",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Count stocks by sector",
            description = "Returns stock count grouped by sector")
    List<Object[]> countStocksBySector();

    @GetMapping(value = APP_ROOT + "/stocks/statistics/by-ownership",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Count stocks by ownership type",
            description = "Returns stock count grouped by ownership type")
    List<Object[]> countStocksByOwnership();


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