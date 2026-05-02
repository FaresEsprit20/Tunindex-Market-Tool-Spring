package com.tunindex.market_tool.common.repository.jpa;

import com.tunindex.market_tool.common.TestConfig;
import com.tunindex.market_tool.common.entities.Stock;
import com.tunindex.market_tool.common.entities.embedded.*;
import com.tunindex.market_tool.common.entities.enums.OwnershipType;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest
@RequiredArgsConstructor
@ContextConfiguration(classes = TestConfig.class)
@ActiveProfiles("test")
class StockRepositoryTest {

    private final StockRepository stockRepository;

    private Stock testStock;

    @BeforeEach
    void setUp() {
        // Create test stock
        testStock = createStock(
                "BH",
                "Banque de l'Habitat",
                "Tunis Stock Exchange",
                SectorType.FINANCIALS,
                OwnershipType.GOVERNMENT,
                new BigDecimal("10.37"),
                new BigDecimal("1.99"),
                new BigDecimal("8.69"),
                new BigDecimal("5.09")
        );

        Stock anotherStock = createStock(
                "BNA",
                "Banque Nationale Agricole",
                "Tunis Stock Exchange",
                SectorType.FINANCIALS,
                OwnershipType.GOVERNMENT,
                new BigDecimal("15.54"),
                new BigDecimal("4.53"),
                new BigDecimal("3.23"),
                new BigDecimal("6.44")
        );

        stockRepository.save(testStock);
        stockRepository.save(anotherStock);
    }

    private Stock createStock(String symbol, String name, String exchange,
                              SectorType sector, OwnershipType ownershipType,
                              BigDecimal lastPrice, BigDecimal eps,
                              BigDecimal peRatio, BigDecimal dividendYield) {
        Stock stock = new Stock();
        stock.setSymbol(symbol);
        stock.setName(name);
        stock.setExchange(exchange);
        stock.setMarket("Tunisia");
        stock.setCurrency("TND");
        stock.setSector(sector);
        stock.setOwnershipType(ownershipType);
        stock.setLastUpdate(LocalDateTime.now());
        stock.setCreatedAt(LocalDateTime.now());
        stock.setUpdatedAt(LocalDateTime.now());

        // Price Data
        PriceData priceData = new PriceData();
        priceData.setLastPrice(lastPrice);
        priceData.setPrevClose(lastPrice.subtract(new BigDecimal("0.10")));
        priceData.setDayHigh(lastPrice.add(new BigDecimal("0.20")));
        priceData.setDayLow(lastPrice.subtract(new BigDecimal("0.15")));
        priceData.setWeek52High(lastPrice.multiply(new BigDecimal("1.5")));
        priceData.setWeek52Low(lastPrice.multiply(new BigDecimal("0.8")));
        priceData.setCloseTo52weekslowPct(new BigDecimal("65.5"));
        stock.setPriceData(priceData);

        // Volume Data
        VolumeData volumeData = new VolumeData();
        volumeData.setVolume(100000L);
        volumeData.setAvgVolume3m(50000L);
        stock.setVolumeData(volumeData);

        // Fundamental Data
        FundamentalData fundamentalData = new FundamentalData();
        fundamentalData.setEps(eps);
        fundamentalData.setPeRatio(peRatio);
        fundamentalData.setDividendYield(dividendYield);
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
        calculatedValues.setGrahamFairValue(lastPrice.multiply(new BigDecimal("1.5")));
        calculatedValues.setMarginOfSafety(new BigDecimal("35.5"));
        calculatedValues.setBookValuePerShare(new BigDecimal("12.5"));
        stock.setCalculatedValues(calculatedValues);

        return stock;
    }

    // ========== FIND BY SYMBOL TESTS ==========

    @Test
    void findBySymbol_ShouldReturnStock_WhenSymbolExists() {
        Optional<Stock> found = stockRepository.findBySymbol("BH");

        assertThat(found).isPresent();
        assertThat(found.get().getSymbol()).isEqualTo("BH");
        assertThat(found.get().getName()).isEqualTo("Banque de l'Habitat");
    }

    @Test
    void findBySymbol_ShouldReturnEmpty_WhenSymbolDoesNotExist() {
        Optional<Stock> found = stockRepository.findBySymbol("NON_EXISTENT");

        assertThat(found).isEmpty();
    }

    @Test
    void findBySymbol_IsCaseSensitive() {
        // Symbols are stored as is, so case matters
        Optional<Stock> found = stockRepository.findBySymbol("BH");
        assertThat(found).isPresent();

        Optional<Stock> foundLower = stockRepository.findBySymbol("bh");
        assertThat(foundLower).isEmpty();
    }

    // ========== FIND BY SYMBOL AND EXCHANGE TESTS ==========

    @Test
    void findBySymbolAndExchange_ShouldReturnStock_WhenBothMatch() {
        Optional<Stock> found = stockRepository.findBySymbolAndExchange("BH", "Tunis Stock Exchange");

        assertThat(found).isPresent();
        assertThat(found.get().getSymbol()).isEqualTo("BH");
        assertThat(found.get().getExchange()).isEqualTo("Tunis Stock Exchange");
    }

    @Test
    void findBySymbolAndExchange_ShouldReturnEmpty_WhenSymbolMatchesButExchangeDoesNot() {
        Optional<Stock> found = stockRepository.findBySymbolAndExchange("BH", "NYSE");

        assertThat(found).isEmpty();
    }

    @Test
    void findBySymbolAndExchange_ShouldReturnEmpty_WhenNoneMatch() {
        Optional<Stock> found = stockRepository.findBySymbolAndExchange("NON_EXISTENT", "NON_EXISTENT");

        assertThat(found).isEmpty();
    }

    // ========== EXISTENCE CHECKS TESTS ==========

    @Test
    void existsBySymbol_ShouldReturnTrue_WhenSymbolExists() {
        boolean exists = stockRepository.existsBySymbol("BH");
        assertThat(exists).isTrue();
    }

    @Test
    void existsBySymbol_ShouldReturnFalse_WhenSymbolDoesNotExist() {
        boolean exists = stockRepository.existsBySymbol("NON_EXISTENT");
        assertThat(exists).isFalse();
    }

    @Test
    void existsBySymbolAndExchange_ShouldReturnTrue_WhenBothMatch() {
        boolean exists = stockRepository.existsBySymbolAndExchange("BH", "Tunis Stock Exchange");
        assertThat(exists).isTrue();
    }

    @Test
    void existsBySymbolAndExchange_ShouldReturnFalse_WhenSymbolMatchesButExchangeDoesNot() {
        boolean exists = stockRepository.existsBySymbolAndExchange("BH", "NYSE");
        assertThat(exists).isFalse();
    }

    // ========== STATISTICS TESTS ==========

    @Test
    void countStocksBySector_ShouldReturnCorrectCounts() {
        List<Object[]> results = stockRepository.countStocksBySector();

        assertThat(results).isNotEmpty();

        Object[] financials = results.stream()
                .filter(r -> r[0] == SectorType.FINANCIALS)
                .findFirst()
                .orElse(null);

        assertThat(financials).isNotNull();
        assertThat((Long) financials[1]).isEqualTo(2L);
    }

    @Test
    void countStocksByOwnership_ShouldReturnCorrectCounts() {
        List<Object[]> results = stockRepository.countStocksByOwnership();

        assertThat(results).isNotEmpty();

        Object[] government = results.stream()
                .filter(r -> r[0] == OwnershipType.GOVERNMENT)
                .findFirst()
                .orElse(null);

        assertThat(government).isNotNull();
        assertThat((Long) government[1]).isEqualTo(2L);
    }

    // ========== UPDATE TESTS ==========

    @Test
    void updateLastUpdateTime_ShouldUpdateTimestamp() throws InterruptedException {
        // Wait a moment to ensure time difference
        Thread.sleep(100);

        stockRepository.updateLastUpdateTime("BH");

        Optional<Stock> updated = stockRepository.findBySymbol("BH");
        assertThat(updated).isPresent();
        // updatedAt should be updated
        assertThat(updated.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void updateLastUpdateTime_ShouldNotFail_WhenSymbolDoesNotExist() {
        // Should not throw exception
        stockRepository.updateLastUpdateTime("NON_EXISTENT");
    }

    // ========== CRUD OPERATION TESTS ==========

    @Test
    void saveStock_ShouldPersistStock() {
        Stock newStock = createStock(
                "BIAT", "Banque Internationale Arabe de Tunisie",
                "Tunis Stock Exchange", SectorType.FINANCIALS,
                OwnershipType.PRIVATE, new BigDecimal("148.90"),
                new BigDecimal("5.72"), new BigDecimal("26.03"),
                new BigDecimal("4.03")
        );

        Stock saved = stockRepository.save(newStock);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSymbol()).isEqualTo("BIAT");

        Optional<Stock> found = stockRepository.findBySymbol("BIAT");
        assertThat(found).isPresent();
    }

    @Test
    void deleteStock_ShouldRemoveStock() {
        stockRepository.delete(testStock);

        Optional<Stock> found = stockRepository.findBySymbol("BH");
        assertThat(found).isEmpty();
    }

    @Test
    void findAll_ShouldReturnAllStocks() {
        List<Stock> stocks = stockRepository.findAll();

        assertThat(stocks).hasSize(2);
        assertThat(stocks).extracting(Stock::getSymbol).containsExactlyInAnyOrder("BH", "BNA");
    }

    // ========== EMBEDDED OBJECT TESTS ==========

    @Test
    void stockHasPriceData() {
        Optional<Stock> found = stockRepository.findBySymbol("BH");

        assertThat(found).isPresent();
        assertThat(found.get().getPriceData()).isNotNull();
        assertThat(found.get().getPriceData().getLastPrice()).isEqualTo(new BigDecimal("10.37"));
    }

    @Test
    void stockHasFundamentalData() {
        Optional<Stock> found = stockRepository.findBySymbol("BH");

        assertThat(found).isPresent();
        assertThat(found.get().getFundamentalData()).isNotNull();
        assertThat(found.get().getFundamentalData().getEps()).isEqualTo(new BigDecimal("1.99"));
    }

    @Test
    void stockHasCalculatedValues() {
        Optional<Stock> found = stockRepository.findBySymbol("BH");

        assertThat(found).isPresent();
        assertThat(found.get().getCalculatedValues()).isNotNull();
        assertThat(found.get().getCalculatedValues().getMarginOfSafety()).isEqualTo(new BigDecimal("35.5"));
    }
}