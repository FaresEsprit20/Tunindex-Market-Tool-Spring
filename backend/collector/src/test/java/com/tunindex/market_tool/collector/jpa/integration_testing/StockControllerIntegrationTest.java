package com.tunindex.market_tool.collector.jpa.integration_testing;

import com.tunindex.market_tool.collector.BaseIntegrationTestConfig;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.common.entities.embedded.*;
import com.tunindex.market_tool.common.entities.enums.OwnershipType;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Drives the stock endpoints through the real application context.
 *
 * <p>Written against WebTestClient rather than MockMvc: this service runs on
 * WebFlux, where MockMvc does not exist. See {@link BaseIntegrationTestConfig}
 * for why that distinction mattered here.
 */
@DisplayName("Stock Controller Integration Tests")
class StockControllerIntegrationTest extends BaseIntegrationTestConfig {

    /**
     * The collector's own path, not the public API's.
     *
     * <p>This used to read {@code "/" + APP_ROOT + "/stocks"} - the route the
     * <em>api service</em> exposes to the browser. The collector serves these
     * under {@code /internal/stock-data}, so every request 404'd. Nobody
     * noticed because the class could not start at all: once the context
     * failure was fixed, eighteen tests turned out to have been aimed at a
     * path that has never existed here.
     */
    private static final String BASE_URL = "/internal/stock-data";

    @Autowired
    private StockRepository stockRepository;

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

    /** POST /filter with a pagination body, which most of these tests need. */
    private WebTestClient.ResponseSpec filter(PaginationAndFilteringDto pagination) {
        return webTestClient.post()
                .uri(BASE_URL + "/filter")
                .contentType(MediaType.APPLICATION_JSON)
                // The DTO is serialised by the client rather than by hand, so
                // these no longer need an ObjectMapper of their own.
                .bodyValue(pagination)
                .exchange();
    }

    /** A page request with the defaults every test starts from. */
    private PaginationAndFilteringDto page(int page, int size) {
        PaginationAndFilteringDto pagination = new PaginationAndFilteringDto();
        pagination.setPage(page);
        pagination.setSize(size);
        return pagination;
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
    void findBySymbol_ShouldReturnStock_WhenSymbolExists() {
        webTestClient.get()
                .uri(BASE_URL + "/symbol/{symbol}", "BH")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("BH")
                .jsonPath("$.name").isEqualTo("Banque de l'Habitat")
                .jsonPath("$.sector").isEqualTo("FINANCIALS")
                .jsonPath("$.lastPrice").isEqualTo(10.37)
                .jsonPath("$.eps").isEqualTo(1.99)
                .jsonPath("$.peRatio").isEqualTo(8.69)
                .jsonPath("$.dividendYield").isEqualTo(5.09)
                .jsonPath("$.grahamFairValue").isEqualTo(15.55)
                .jsonPath("$.marginOfSafety").isEqualTo(35.5);
    }

    @Test
    @DisplayName("GET /stocks/symbol/{symbol} - Should return 404 when symbol not found")
    void findBySymbol_ShouldReturn404_WhenSymbolNotFound() {
        webTestClient.get()
                .uri(BASE_URL + "/symbol/{symbol}", "NONEXISTENT")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET /stocks/symbol/{symbol} - Should return 404 when symbol is empty")
    void findBySymbol_ShouldReturn404_WhenSymbolIsEmpty() {
        webTestClient.get()
                .uri(BASE_URL + "/symbol/{symbol}", "")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ========== FIND BY SYMBOL AND EXCHANGE TESTS ==========

    @Test
    @DisplayName("GET /stocks/symbol/{symbol}/exchange/{exchange} - Should return stock when both match")
    void findBySymbolAndExchange_ShouldReturnStock_WhenBothMatch() {
        webTestClient.get()
                .uri(BASE_URL + "/symbol/{symbol}/exchange/{exchange}", "BH", "Tunis Stock Exchange")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.symbol").isEqualTo("BH")
                .jsonPath("$.exchange").isEqualTo("Tunis Stock Exchange");
    }

    @Test
    @DisplayName("GET /stocks/symbol/{symbol}/exchange/{exchange} - Should return 404 when exchange doesn't match")
    void findBySymbolAndExchange_ShouldReturn404_WhenExchangeDoesNotMatch() {
        webTestClient.get()
                .uri(BASE_URL + "/symbol/{symbol}/exchange/{exchange}", "BH", "NYSE")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET /stocks/symbol/{symbol}/exchange/{exchange} - Should return 404 when exchange is empty")
    void findBySymbolAndExchange_ShouldReturn404_WhenExchangeIsEmpty() {
        webTestClient.get()
                .uri(BASE_URL + "/symbol/{symbol}/exchange/{exchange}", "BH", "")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ========== FILTER STOCKS TESTS ==========

    @Test
    @DisplayName("POST /stocks/filter - Should return paginated stocks with default values")
    void filterStocks_ShouldReturnPaginatedStocks_WithDefaultValues() {
        filter(page(1, 10))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.totalElements").isEqualTo(4)
                .jsonPath("$.totalPages").isEqualTo(1)
                .jsonPath("$.page").isEqualTo(1)
                .jsonPath("$.size").isEqualTo(10);
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by sector")
    void filterStocks_ShouldFilterBySector() {
        PaginationAndFilteringDto pagination = page(1, 10);
        Map<String, String> filters = new HashMap<>();
        filters.put("sector", "FINANCIALS");
        pagination.setFilters(filters);

        filter(pagination)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.content[*].sector").value(everyItem(equalTo("FINANCIALS")));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by ownership type")
    void filterStocks_ShouldFilterByOwnershipType() {
        PaginationAndFilteringDto pagination = page(1, 10);
        Map<String, String> filters = new HashMap<>();
        filters.put("ownershipType", "PRIVATE");
        pagination.setFilters(filters);

        filter(pagination)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[*].ownershipType").value(everyItem(equalTo("PRIVATE")));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by price range")
    void filterStocks_ShouldFilterByPriceRange() {
        PaginationAndFilteringDto pagination = page(1, 10);
        Map<String, String> filters = new HashMap<>();
        filters.put("minPrice", "10");
        filters.put("maxPrice", "11");
        pagination.setFilters(filters);

        filter(pagination)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by undervalued flag")
    void filterStocks_ShouldFilterByUndervaluedFlag() {
        PaginationAndFilteringDto pagination = page(1, 10);
        Map<String, String> filters = new HashMap<>();
        filters.put("undervalued", "true");
        pagination.setFilters(filters);

        filter(pagination)
                .expectStatus().isOk()
                .expectBody()
                // A flexible matcher, because the figure deserialises as
                // Integer or Double depending on whether it has a fraction.
                .jsonPath("$.content[*].marginOfSafety")
                .value(everyItem(greaterThan(BigDecimal.ZERO.doubleValue())));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should filter by graham criteria preset")
    void filterStocks_ShouldFilterByGrahamCriteria() {
        PaginationAndFilteringDto pagination = page(1, 10);
        Map<String, String> filters = new HashMap<>();
        filters.put("grahamCriteria", "true");
        pagination.setFilters(filters);

        filter(pagination).expectStatus().isOk();
    }

    @Test
    @DisplayName("POST /stocks/filter - Should sort by price descending")
    void filterStocks_ShouldSortByPriceDescending() {
        PaginationAndFilteringDto pagination = page(1, 10);
        pagination.setSortField("lastPrice");
        pagination.setSortDirection(SortingDirection.DESC);

        filter(pagination)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content[0].lastPrice").value(greaterThanOrEqualTo(10.37));
    }

    @Test
    @DisplayName("POST /stocks/filter - Should return 400 when page is invalid")
    void filterStocks_ShouldReturn400_WhenPageIsInvalid() {
        filter(page(0, 10)).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /stocks/filter - Should return 400 when size exceeds limit")
    void filterStocks_ShouldReturn400_WhenSizeExceedsLimit() {
        filter(page(1, 200)).expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /stocks/filter - Should handle empty filters")
    void filterStocks_ShouldHandleEmptyFilters() {
        PaginationAndFilteringDto pagination = page(1, 10);
        pagination.setFilters(new HashMap<>());

        filter(pagination)
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(4);
    }

    // ========== STATISTICS TESTS ==========

    @Test
    @DisplayName("GET /stocks/statistics/by-sector - Should return sector statistics")
    void countStocksBySector_ShouldReturnSectorStatistics() {
        webTestClient.get()
                .uri(BASE_URL + "/statistics/by-sector")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[?(@[0] == 'FINANCIALS')][1]").isEqualTo(3)
                .jsonPath("$[?(@[0] == 'INDUSTRIALS')][1]").isEqualTo(1);
    }

    @Test
    @DisplayName("GET /stocks/statistics/by-ownership - Should return ownership statistics")
    void countStocksByOwnership_ShouldReturnOwnershipStatistics() {
        webTestClient.get()
                .uri(BASE_URL + "/statistics/by-ownership")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[?(@[0] == 'GOVERNMENT')][1]").isEqualTo(2)
                .jsonPath("$[?(@[0] == 'PRIVATE')][1]").isEqualTo(2);
    }

    // ========== REFRESH STOCK DATA TESTS ==========

    @Test
    @DisplayName("PUT /stocks/refresh/{symbol} - Should find a known symbol rather than 404")
    void refreshStockData_ShouldRefresh_WhenSymbolExists() {
        // Asserts only that the symbol is found, not that the refresh
        // succeeds. Refreshing genuinely calls an external provider, so
        // demanding 200 would make this test pass or fail on whether a
        // third-party website is up and whether it is throttling us today -
        // which says nothing about this code. What is worth pinning is the
        // part that is ours: a symbol in the database resolves.
        webTestClient.put()
                .uri(BASE_URL + "/refresh/{symbol}", "BH")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(404));
    }

    @Test
    @DisplayName("PUT /stocks/refresh/{symbol} - Should return 404 when symbol not found")
    void refreshStockData_ShouldReturn404_WhenSymbolNotFound() {
        webTestClient.put()
                .uri(BASE_URL + "/refresh/{symbol}", "NONEXISTENT")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("PUT /stocks/refresh/{symbol} - Should return 404 when symbol is empty")
    void refreshStockData_ShouldReturn404_WhenSymbolIsEmpty() {
        webTestClient.put()
                .uri(BASE_URL + "/refresh/{symbol}", "")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ========== COMPLEX FILTER TESTS ==========

    @Test
    @DisplayName("POST /stocks/filter - Should combine multiple filters")
    void filterStocks_ShouldCombineMultipleFilters() {
        PaginationAndFilteringDto pagination = page(1, 10);
        Map<String, String> filters = new HashMap<>();
        filters.put("sector", "FINANCIALS");
        filters.put("ownershipType", "PRIVATE");
        filters.put("minMarginOfSafety", "30");
        pagination.setFilters(filters);

        filter(pagination).expectStatus().isOk();
    }

    @Test
    @DisplayName("POST /stocks/filter - Should support pagination with custom page size")
    void filterStocks_ShouldSupportPaginationWithCustomPageSize() {
        filter(page(1, 2))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isArray()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.totalPages").isEqualTo(2);
    }

    @Test
    @DisplayName("POST /stocks/filter - Should return second page correctly")
    void filterStocks_ShouldReturnSecondPage() {
        filter(page(2, 2))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.page").isEqualTo(2);
    }
}
