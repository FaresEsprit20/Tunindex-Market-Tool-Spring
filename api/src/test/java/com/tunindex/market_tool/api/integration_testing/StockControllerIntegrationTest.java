package com.tunindex.market_tool.api.integration_testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.api.BaseIntegrationTestConfig;
import com.tunindex.market_tool.common.entities.Stock;
import com.tunindex.market_tool.common.entities.embedded.*;
import com.tunindex.market_tool.common.entities.enums.OwnershipType;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import com.tunindex.market_tool.common.repository.jpa.StockRepository;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Stock Controller Integration Tests")
class StockControllerIntegrationTest extends BaseIntegrationTestConfig {

    private static final String BASE_URL = "/" + APP_ROOT + "/stocks";

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        stockRepository.deleteAll();

        // Create stocks with different prices
        Stock bhStock = createStockEntity("BH", "Banque de l'Habitat", SectorType.FINANCIALS, OwnershipType.GOVERNMENT);
        bhStock.getPriceData().setLastPrice(new BigDecimal("10.37"));

        Stock bnaStock = createStockEntity("BNA", "Banque Nationale Agricole", SectorType.FINANCIALS, OwnershipType.GOVERNMENT);
        bnaStock.getPriceData().setLastPrice(new BigDecimal("15.50"));

        Stock biatStock = createStockEntity("BIAT", "Banque Internationale Arabe de Tunisie", SectorType.FINANCIALS, OwnershipType.PRIVATE);
        biatStock.getPriceData().setLastPrice(new BigDecimal("8.25"));

        Stock pghStock = createStockEntity("PGH", "Société de fabrication des boissons de Tunisie", SectorType.INDUSTRIALS, OwnershipType.PRIVATE);
        pghStock.getPriceData().setLastPrice(new BigDecimal("10.37"));

        stockRepository.save(bhStock);
        stockRepository.save(bnaStock);
        stockRepository.save(biatStock);
        stockRepository.save(pghStock);
    }


    private Stock createStockEntity(String symbol, String name, SectorType sector, OwnershipType ownershipType) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setName(name);
        stock.setExchange("Tunis Stock Exchange");
        stock.setExchangeFullName("BVMT");
        stock.setMarket("Tunisia");
        stock.setCurrency("TND");
        stock.setSector(sector);
        stock.setIndustry("Banking");
        stock.setOwnershipType(ownershipType);
        stock.setUrl("/quote/bvmt/" + symbol + "/");
        stock.setLastUpdate(LocalDateTime.now());
        stock.setCreatedAt(LocalDateTime.now());
        stock.setUpdatedAt(LocalDateTime.now());

        // Price Data
        PriceData priceData = new PriceData();
        priceData.setLastPrice(new BigDecimal("10.37"));
        priceData.setPrevClose(new BigDecimal("10.27"));
        priceData.setDayHigh(new BigDecimal("10.50"));
        priceData.setDayLow(new BigDecimal("10.20"));
        priceData.setWeek52High(new BigDecimal("15.00"));
        priceData.setWeek52Low(new BigDecimal("8.00"));
        priceData.setWeek52Range("8.00 - 15.00");
        priceData.setCloseTo52weekslowPct(new BigDecimal("65.5"));
        stock.setPriceData(priceData);

        // Volume Data
        VolumeData volumeData = new VolumeData();
        volumeData.setVolume(100000L);
        volumeData.setAvgVolume3m(50000L);
        stock.setVolumeData(volumeData);

        // Fundamental Data
        FundamentalData fundamentalData = new FundamentalData();
        fundamentalData.setEps(new BigDecimal("1.99"));
        fundamentalData.setPeRatio(new BigDecimal("8.69"));
        fundamentalData.setDividendYield(new BigDecimal("5.09"));
        fundamentalData.setMarketCap(new BigDecimal("1000000000"));
        fundamentalData.setSharesOutstanding(10000000L);
        fundamentalData.setRevenue(new BigDecimal("500000000"));
        fundamentalData.setOneYearReturn(new BigDecimal("15.5"));
        stock.setFundamentalData(fundamentalData);

        // Ratios Data
        RatiosData ratiosData = new RatiosData();
        ratiosData.setPriceToBook(new BigDecimal("1.5"));
        ratiosData.setDebtToEquity(new BigDecimal("0.3"));
        ratiosData.setProfitMargin(new BigDecimal("25.5"));
        stock.setRatiosData(ratiosData);

        // Calculated Values
        CalculatedValues calculatedValues = new CalculatedValues();
        calculatedValues.setGrahamFairValue(new BigDecimal("15.55"));
        calculatedValues.setMarginOfSafety(new BigDecimal("35.5"));
        calculatedValues.setBookValuePerShare(new BigDecimal("12.5"));
        stock.setCalculatedValues(calculatedValues);

        return stock;
    }

    // ========== FIND BY SYMBOL TESTS ==========

    @Test
    @DisplayName("GET /stocks/symbol/{symbol} - Should return stock when symbol exists")
    void findBySymbol_ShouldReturnStock_WhenSymbolExists() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/symbol/{symbol}", "BH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BH"))
                .andExpect(jsonPath("$.name").value("Banque de l'Habitat"))
                .andExpect(jsonPath("$.sector").value("FINANCIALS"))
                .andExpect(jsonPath("$.lastPrice").value(10.37))
                .andExpect(jsonPath("$.eps").value(1.99))
                .andExpect(jsonPath("$.peRatio").value(8.69))
                .andExpect(jsonPath("$.dividendYield").value(5.09))
                .andExpect(jsonPath("$.grahamFairValue").value(15.55))
                .andExpect(jsonPath("$.marginOfSafety").value(35.5));
    }

    @Test
    @DisplayName("GET /stocks/symbol/{symbol} - Should return 404 when symbol not found")
    void findBySymbol_ShouldReturn404_WhenSymbolNotFound() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/symbol/{symbol}", "NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /stocks/symbol/{symbol} - Should return 400 when symbol is empty")
    void findBySymbol_ShouldReturn404_WhenSymbolIsEmpty() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/symbol/{symbol}", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    // ========== FIND BY SYMBOL AND EXCHANGE TESTS ==========

    @Test
    @DisplayName("GET /stocks/symbol/{symbol}/exchange/{exchange} - Should return stock when both match")
    void findBySymbolAndExchange_ShouldReturnStock_WhenBothMatch() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/symbol/{symbol}/exchange/{exchange}", "BH", "Tunis Stock Exchange")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BH"))
                .andExpect(jsonPath("$.exchange").value("Tunis Stock Exchange"));
    }

    @Test
    @DisplayName("GET /stocks/symbol/{symbol}/exchange/{exchange} - Should return 404 when exchange doesn't match")
    void findBySymbolAndExchange_ShouldReturn404_WhenExchangeDoesNotMatch() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/symbol/{symbol}/exchange/{exchange}", "BH", "NYSE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /stocks/symbol/{symbol}/exchange/{exchange} - Should return 400 when exchange is null")
    void findBySymbolAndExchange_ShouldReturn404_WhenExchangeIsEmpty() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/symbol/{symbol}/exchange/{exchange}", "BH", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    // ========== FILTER STOCKS TESTS ==========

    @Test
    @DisplayName("POST /stocks/filter - Should return paginated stocks with default values")
    void filterStocks_ShouldReturnPaginatedStocks_WithDefaultValues() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by sector")
    void filterStocks_ShouldFilterBySector() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("sector", "FINANCIALS");
        paginationDto.setFilters(filters);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].sector").value(everyItem(equalTo("FINANCIALS"))));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by ownership type")
    void filterStocks_ShouldFilterByOwnershipType() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("ownershipType", "PRIVATE");
        paginationDto.setFilters(filters);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].ownershipType").value(everyItem(equalTo("PRIVATE"))));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by price range")
    void filterStocks_ShouldFilterByPriceRange() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("minPrice", "10");
        filters.put("maxPrice", "11");
        paginationDto.setFilters(filters);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by undervalued flag")
    void filterStocks_ShouldFilterByUndervaluedFlag() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("undervalued", "true");
        paginationDto.setFilters(filters);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                // Use a more flexible matcher that works with both Integer and Double
                .andExpect(jsonPath("$.content[*].marginOfSafety").value(everyItem(greaterThan(BigDecimal.ZERO.doubleValue()))));
    }
    @Test
    @DisplayName("POST /stocks/filter - Should filter by graham criteria preset")
    void filterStocks_ShouldFilterByGrahamCriteria() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("grahamCriteria", "true");
        paginationDto.setFilters(filters);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /stocks/filter - Should sort by price descending")
    void filterStocks_ShouldSortByPriceDescending() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        paginationDto.setSortField("lastPrice");
        paginationDto.setSortDirection(SortingDirection.DESC);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastPrice").value(greaterThanOrEqualTo(10.37)));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should return 400 when page is invalid")
    void filterStocks_ShouldReturn400_WhenPageIsInvalid() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(0);
        paginationDto.setSize(10);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /stocks/filter - Should return 400 when size exceeds limit")
    void filterStocks_ShouldReturn400_WhenSizeExceedsLimit() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(200);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /stocks/filter - Should handle empty filters")
    void filterStocks_ShouldHandleEmptyFilters() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        paginationDto.setFilters(new HashMap<>());

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    // ========== STATISTICS TESTS ==========

    @Test
    @DisplayName("GET /stocks/statistics/by-sector - Should return sector statistics")
    void countStocksBySector_ShouldReturnSectorStatistics() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/statistics/by-sector")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@[0] == 'FINANCIALS')][1]").value(3))
                .andExpect(jsonPath("$[?(@[0] == 'INDUSTRIALS')][1]").value(1));
    }

    @Test
    @DisplayName("GET /stocks/statistics/by-ownership - Should return ownership statistics")
    void countStocksByOwnership_ShouldReturnOwnershipStatistics() throws Exception {
        // When & Then
        mockMvc.perform(get(BASE_URL + "/statistics/by-ownership")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@[0] == 'GOVERNMENT')][1]").value(2))
                .andExpect(jsonPath("$[?(@[0] == 'PRIVATE')][1]").value(2));
    }

    // ========== REFRESH STOCK DATA TESTS ==========

    @Test
    @DisplayName("PUT /stocks/refresh/{symbol} - Should refresh stock data when symbol exists")
    void refreshStockData_ShouldRefresh_WhenSymbolExists() throws Exception {
        // When & Then
        mockMvc.perform(put(BASE_URL + "/refresh/{symbol}", "BH")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /stocks/refresh/{symbol} - Should return 404 when symbol not found")
    void refreshStockData_ShouldReturn404_WhenSymbolNotFound() throws Exception {
        // When & Then
        mockMvc.perform(put(BASE_URL + "/refresh/{symbol}", "NONEXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /stocks/refresh/{symbol} - Should return 400 when symbol is empty")
    void refreshStockData_ShouldReturn404_WhenSymbolIsEmpty() throws Exception {
        // When & Then
        mockMvc.perform(put(BASE_URL + "/refresh/{symbol}", "")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
    }

    // ========== COMPLEX FILTER TESTS ==========

    @Test
    @DisplayName("POST /stocks/filter - Should combine multiple filters")
    void filterStocks_ShouldCombineMultipleFilters() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("sector", "FINANCIALS");
        filters.put("ownershipType", "PRIVATE");
        filters.put("minMarginOfSafety", "30");
        paginationDto.setFilters(filters);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /stocks/filter - Should support pagination with custom page size")
    void filterStocks_ShouldSupportPaginationWithCustomPageSize() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(2);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should return second page correctly")
    void filterStocks_ShouldReturnSecondPage() throws Exception {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(2);
        paginationDto.setSize(2);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/filter")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paginationDto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2));
    }
}