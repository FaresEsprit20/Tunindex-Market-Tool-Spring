package com.tunindex.market_tool.api.controllers.stock;

import com.tunindex.market_tool.api.dto.stock.StockResponseDto;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StockController implements StockApi {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/stock-data";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @Override
    public StockResponseDto findBySymbol(String symbol) {
        log.info("API calling Collector for symbol: {}", symbol);

        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidEntityException(
                    "Symbol cannot be empty",
                    ErrorCodes.EMPTY_SYMBOL,
                    Collections.singletonList("Symbol parameter is required")
            );
        }

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/symbol/{symbol}", symbol)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(StockResponseDto.class)
                .block();
    }

    @Override
    public StockResponseDto findBySymbolAndExchange(String symbol, String exchange) {
        log.info("API calling Collector for symbol: {} exchange: {}", symbol, exchange);

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/symbol/{symbol}/exchange/{exchange}", symbol, exchange)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(StockResponseDto.class)
                .block();
    }

    @Override
    public PagedResponse<StockResponseDto> filterStocks(PaginationAndFilteringDto paginationDto) {
        log.info("API calling Collector to filter stocks");

        return webClientBuilder.build()
                .post()
                .uri(COLLECTOR_URL + "/filter")
                .header("X-API-Key", internalApiKey)
                .bodyValue(paginationDto)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<PagedResponse<StockResponseDto>>() {})
                .block();
    }

    @Override
    public List<Object[]> countStocksBySector() {
        log.info("API calling Collector to count by sector");

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/statistics/by-sector")
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Object[]>>() {})
                .block();
    }

    @Override
    public List<Object[]> countStocksByOwnership() {
        log.info("API calling Collector to count by ownership");

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/statistics/by-ownership")
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Object[]>>() {})
                .block();
    }

    @Override
    public void refreshStockData(String symbol) {
        log.info("API calling Collector to refresh: {}", symbol);

        webClientBuilder.build()
                .put()
                .uri(COLLECTOR_URL + "/refresh/{symbol}", symbol)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}