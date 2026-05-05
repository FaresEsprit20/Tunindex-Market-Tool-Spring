package com.tunindex.market_tool.api.controllers.stock;

import com.tunindex.market_tool.api.dto.providers.investingcom.StockDto;
import com.tunindex.market_tool.api.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.api.utils.pagination.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

import static com.tunindex.market_tool.api.utils.constants.Constants.APP_ROOT;

@Tag(name = "Stocks", description = "API for stock market operations")
@Validated
public interface StockApi {

    @GetMapping(value = APP_ROOT + "/stocks/symbol/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Find stock by symbol", description = "Search for a stock by its trading symbol")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock found"),
            @ApiResponse(responseCode = "404", description = "Stock not found with given symbol"),
            @ApiResponse(responseCode = "400", description = "Invalid symbol")
    })
    StockDto findBySymbol(@PathVariable("symbol") @NotBlank(message = "Symbol cannot be empty") String symbol);

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
            @PathVariable("symbol") @NotBlank(message = "Symbol cannot be empty") String symbol,
            @PathVariable("exchange") @NotBlank(message = "Exchange cannot be empty") String exchange);

    @PostMapping(value = APP_ROOT + "/stocks/filter",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Filter stocks with advanced criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Filtered stocks retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters")
    })
    PagedResponse<StockDto> filterStocks(@RequestBody @Valid PaginationAndFilteringDto paginationDto);

    @GetMapping(value = APP_ROOT + "/stocks/statistics/by-sector",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Count stocks by sector")
    List<Object[]> countStocksBySector();

    @GetMapping(value = APP_ROOT + "/stocks/statistics/by-ownership",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Count stocks by ownership type")
    List<Object[]> countStocksByOwnership();

    @PutMapping(value = APP_ROOT + "/stocks/refresh/{symbol}")
    @Operation(summary = "Refresh stock data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock refresh initiated"),
            @ApiResponse(responseCode = "404", description = "Stock not found"),
            @ApiResponse(responseCode = "400", description = "Invalid symbol")
    })
    void refreshStockData(@PathVariable("symbol") @NotBlank(message = "Symbol cannot be empty") String symbol);
}