package com.tunindex.market_tool.api.unit_testing;

import com.tunindex.market_tool.api.dto.providers.investingcom.StockDto;
import com.tunindex.market_tool.api.entities.Stock;
import com.tunindex.market_tool.api.entities.embedded.*;
import com.tunindex.market_tool.api.entities.enums.OwnershipType;
import com.tunindex.market_tool.api.entities.enums.SectorType;
import com.tunindex.market_tool.api.exception.EntityNotFoundException;
import com.tunindex.market_tool.api.exception.InvalidEntityException;
import com.tunindex.market_tool.api.repository.jpa.StockRepository;
import com.tunindex.market_tool.api.services.StockServiceImpl;
import com.tunindex.market_tool.api.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.api.utils.pagination.enums.SortingDirection;
import com.tunindex.market_tool.api.utils.pagination.response.PagedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Stock Service Tests")
class StockServiceImplTest {

    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private StockServiceImpl stockService;

    private Stock testStock;
    private Stock anotherStock;

    @BeforeEach
    void setUp() {
        // Create test stock
        testStock = createTestStock("BH", "Banque de l'Habitat");
        anotherStock = createTestStock("BNA", "Banque Nationale Agricole");

        StockDto testStockDto = StockDto.fromEntity(testStock);
    }

    private Stock createTestStock(String symbol, String name) {
        Stock stock = new Stock();
        stock.setId(1L);
        stock.setSymbol(symbol);
        stock.setName(name);
        stock.setExchange("Tunis Stock Exchange");
        stock.setMarket("Tunisia");
        stock.setCurrency("TND");
        stock.setSector(SectorType.FINANCIALS);
        stock.setOwnershipType(OwnershipType.GOVERNMENT);
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
    @DisplayName("Should find stock by symbol successfully")
    void shouldFindStockBySymbol() {
        // Given
        when(stockRepository.findBySymbol("BH")).thenReturn(Optional.of(testStock));

        // When
        StockDto result = stockService.findBySymbol("BH");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("BH");
        assertThat(result.getName()).isEqualTo("Banque de l'Habitat");
        verify(stockRepository, times(1)).findBySymbol("BH");
    }

    @Test
    @DisplayName("Should throw exception when symbol is null")
    void shouldThrowExceptionWhenSymbolIsNull() {
        // When & Then
        assertThrows(InvalidEntityException.class, () -> stockService.findBySymbol(null));
        verify(stockRepository, never()).findBySymbol(anyString());
    }

    @Test
    @DisplayName("Should throw exception when symbol is empty")
    void shouldThrowExceptionWhenSymbolIsEmpty() {
        // When & Then
        assertThrows(InvalidEntityException.class, () -> stockService.findBySymbol(""));
        verify(stockRepository, never()).findBySymbol(anyString());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when stock not found")
    void shouldThrowEntityNotFoundExceptionWhenStockNotFound() {
        // Given
        when(stockRepository.findBySymbol("NONEXISTENT")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(EntityNotFoundException.class, () -> stockService.findBySymbol("NONEXISTENT"));
        verify(stockRepository, times(1)).findBySymbol("NONEXISTENT");
    }

    // ========== FIND BY SYMBOL AND EXCHANGE TESTS ==========

    @Test
    @DisplayName("Should find stock by symbol and exchange successfully")
    void shouldFindStockBySymbolAndExchange() {
        // Given
        when(stockRepository.findBySymbolAndExchange("BH", "Tunis Stock Exchange"))
                .thenReturn(Optional.of(testStock));

        // When
        StockDto result = stockService.findBySymbolAndExchange("BH", "Tunis Stock Exchange");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo("BH");
        verify(stockRepository, times(1)).findBySymbolAndExchange("BH", "Tunis Stock Exchange");
    }

    @Test
    @DisplayName("Should throw exception when exchange is null")
    void shouldThrowExceptionWhenExchangeIsNull() {
        // When & Then
        assertThrows(InvalidEntityException.class,
                () -> stockService.findBySymbolAndExchange("BH", null));
        verify(stockRepository, never()).findBySymbolAndExchange(anyString(), anyString());
    }

    // ========== FILTER STOCKS TESTS ==========

    @Test
    @DisplayName("Should filter stocks with pagination")
    void shouldFilterStocksWithPagination() {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        paginationDto.setSortField("symbol");
        paginationDto.setSortDirection(SortingDirection.ASC);

        Pageable pageable = PageRequest.of(0, 10);
        List<Stock> stocks = List.of(testStock, anotherStock);
        Page<Stock> stockPage = new PageImpl<>(stocks, pageable, 2);

        when(stockRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(stockPage);

        // When
        PagedResponse<StockDto> result = stockService.filterStocks(paginationDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        verify(stockRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Should handle empty filters gracefully")
    void shouldHandleEmptyFilters() {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        paginationDto.setFilters(new HashMap<>());

        Pageable pageable = PageRequest.of(0, 10);
        Page<Stock> stockPage = new PageImpl<>(List.of(testStock), pageable, 1);

        when(stockRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(stockPage);

        // When
        PagedResponse<StockDto> result = stockService.filterStocks(paginationDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(stockRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Should validate pagination parameters")
    void shouldValidatePaginationParameters() {
        // Given - invalid page
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(0);
        paginationDto.setSize(10);

        // When & Then
        assertThrows(InvalidEntityException.class, () -> stockService.filterStocks(paginationDto));
        verify(stockRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Should validate page size limit")
    void shouldValidatePageSizeLimit() {
        // Given - size exceeds 100
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(200);

        // When & Then
        assertThrows(InvalidEntityException.class, () -> stockService.filterStocks(paginationDto));
        verify(stockRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    // ========== COUNT STOCKS BY SECTOR TESTS ==========

    @Test
    @DisplayName("Should count stocks by sector")
    void shouldCountStocksBySector() {
        // Given
        List<Object[]> expectedResults = List.of(
                new Object[]{SectorType.FINANCIALS, 2L},
                new Object[]{SectorType.INDUSTRIALS, 1L}
        );
        when(stockRepository.countStocksBySector()).thenReturn(expectedResults);

        // When
        List<Object[]> results = stockService.countStocksBySector();

        // Then
        assertThat(results).isNotNull();
        assertThat(results).hasSize(2);
        verify(stockRepository, times(1)).countStocksBySector();
    }

    // ========== COUNT STOCKS BY OWNERSHIP TESTS ==========

    @Test
    @DisplayName("Should count stocks by ownership type")
    void shouldCountStocksByOwnership() {
        // Given
        List<Object[]> expectedResults = List.of(
                new Object[]{OwnershipType.GOVERNMENT, 2L},
                new Object[]{OwnershipType.PRIVATE, 1L}
        );
        when(stockRepository.countStocksByOwnership()).thenReturn(expectedResults);

        // When
        List<Object[]> results = stockService.countStocksByOwnership();

        // Then
        assertThat(results).isNotNull();
        assertThat(results).hasSize(2);
        verify(stockRepository, times(1)).countStocksByOwnership();
    }

    // ========== REFRESH STOCK DATA TESTS ==========

    @Test
    @DisplayName("Should refresh stock data successfully")
    void shouldRefreshStockData() {
        // Given
        when(stockRepository.existsBySymbol("BH")).thenReturn(true);
        doNothing().when(stockRepository).updateLastUpdateTime("BH");

        // When
        stockService.refreshStockData("BH");

        // Then
        verify(stockRepository, times(1)).existsBySymbol("BH");
        verify(stockRepository, times(1)).updateLastUpdateTime("BH");
    }

    @Test
    @DisplayName("Should throw exception when refreshing non-existent stock")
    void shouldThrowExceptionWhenRefreshingNonExistentStock() {
        // Given
        when(stockRepository.existsBySymbol("NONEXISTENT")).thenReturn(false);

        // When & Then
        assertThrows(EntityNotFoundException.class,
                () -> stockService.refreshStockData("NONEXISTENT"));
        verify(stockRepository, times(1)).existsBySymbol("NONEXISTENT");
        verify(stockRepository, never()).updateLastUpdateTime(anyString());
    }

    // ========== FILTERS WITH SPECIFICATIONS TESTS ==========

    @Test
    @DisplayName("Should apply sector filter")
    void shouldApplySectorFilter() {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("sector", "FINANCIALS");
        paginationDto.setFilters(filters);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Stock> stockPage = new PageImpl<>(List.of(testStock), pageable, 1);

        when(stockRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(stockPage);

        // When
        PagedResponse<StockDto> result = stockService.filterStocks(paginationDto);

        // Then
        assertThat(result).isNotNull();
        verify(stockRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Should apply price range filter")
    void shouldApplyPriceRangeFilter() {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("minPrice", "10");
        filters.put("maxPrice", "20");
        paginationDto.setFilters(filters);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Stock> stockPage = new PageImpl<>(List.of(testStock), pageable, 1);

        when(stockRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(stockPage);

        // When
        PagedResponse<StockDto> result = stockService.filterStocks(paginationDto);

        // Then
        assertThat(result).isNotNull();
        verify(stockRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Should apply margin of safety filter")
    void shouldApplyMarginOfSafetyFilter() {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("minMarginOfSafety", "20");
        filters.put("maxMarginOfSafety", "50");
        paginationDto.setFilters(filters);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Stock> stockPage = new PageImpl<>(List.of(testStock), pageable, 1);

        when(stockRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(stockPage);

        // When
        PagedResponse<StockDto> result = stockService.filterStocks(paginationDto);

        // Then
        assertThat(result).isNotNull();
        verify(stockRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Should apply undervalued flag filter")
    void shouldApplyUndervaluedFlag() {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("undervalued", "true");
        paginationDto.setFilters(filters);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Stock> stockPage = new PageImpl<>(List.of(testStock), pageable, 1);

        when(stockRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(stockPage);

        // When
        PagedResponse<StockDto> result = stockService.filterStocks(paginationDto);

        // Then
        assertThat(result).isNotNull();
        verify(stockRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Should apply Graham criteria preset")
    void shouldApplyGrahamCriteriaPreset() {
        // Given
        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        Map<String, String> filters = new HashMap<>();
        filters.put("grahamCriteria", "true");
        paginationDto.setFilters(filters);

        Pageable pageable = PageRequest.of(0, 10);
        Page<Stock> stockPage = new PageImpl<>(List.of(testStock), pageable, 1);

        when(stockRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(stockPage);

        // When
        PagedResponse<StockDto> result = stockService.filterStocks(paginationDto);

        // Then
        assertThat(result).isNotNull();
        verify(stockRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    // ========== DTO CONVERSION TESTS ==========

    @Test
    @DisplayName("Should convert Stock entity to StockDto correctly")
    void shouldConvertEntityToDtoCorrectly() {
        // Given
        when(stockRepository.findBySymbol("BH")).thenReturn(Optional.of(testStock));

        // When
        StockDto result = stockService.findBySymbol("BH");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSymbol()).isEqualTo(testStock.getSymbol());
        assertThat(result.getName()).isEqualTo(testStock.getName());
        assertThat(result.getLastPrice()).isEqualTo(testStock.getPriceData().getLastPrice());
        assertThat(result.getEps()).isEqualTo(testStock.getFundamentalData().getEps());
        assertThat(result.getPeRatio()).isEqualTo(testStock.getFundamentalData().getPeRatio());
        assertThat(result.getGrahamFairValue()).isEqualTo(testStock.getCalculatedValues().getGrahamFairValue());
        assertThat(result.getMarginOfSafety()).isEqualTo(testStock.getCalculatedValues().getMarginOfSafety());
    }
}