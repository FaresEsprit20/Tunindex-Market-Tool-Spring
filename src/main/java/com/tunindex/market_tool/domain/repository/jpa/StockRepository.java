package com.tunindex.market_tool.domain.repository.jpa;

import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.entities.enums.OwnershipType;
import com.tunindex.market_tool.domain.entities.enums.SectorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    // Basic finders
    Optional<Stock> findBySymbol(String symbol);

    Optional<Stock> findBySymbolAndExchange(String symbol, String exchange);

    List<Stock> findByExchange(String exchange);

    List<Stock> findBySector(SectorType sector);

    List<Stock> findByOwnershipType(OwnershipType ownershipType);

    // Price based queries
    List<Stock> findByPriceDataLastPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);

    List<Stock> findByPriceDataChangePctGreaterThan(BigDecimal percentage);

    List<Stock> findByPriceDataChangePctLessThan(BigDecimal percentage);

    // Fundamental based queries
    List<Stock> findByFundamentalDataPeRatioLessThan(BigDecimal peRatio);

    List<Stock> findByFundamentalDataPeRatioBetween(BigDecimal minPe, BigDecimal maxPe);

    List<Stock> findByFundamentalDataDividendYieldGreaterThan(BigDecimal yield);

    List<Stock> findByFundamentalDataMarketCapGreaterThan(BigDecimal marketCap);

    List<Stock> findByFundamentalDataMarketCapBetween(BigDecimal minCap, BigDecimal maxCap);

    // Calculated values queries
    List<Stock> findByCalculatedValuesMarginOfSafetyGreaterThan(BigDecimal margin);

    List<Stock> findByCalculatedValuesMarginOfSafetyLessThan(BigDecimal margin);

    List<Stock> findByCalculatedValuesGrahamFairValueIsNotNull();

    // Undervalued stocks (margin of safety > 20%)
    @Query("SELECT s FROM Stock s WHERE s.calculatedValues.marginOfSafety > 20")
    List<Stock> findUndervaluedStocks();

    // Overvalued stocks (margin of safety < -20%)
    @Query("SELECT s FROM Stock s WHERE s.calculatedValues.marginOfSafety < -20")
    List<Stock> findOvervaluedStocks();

    // Near 52-week low (within 10% of low)
    @Query("SELECT s FROM Stock s WHERE s.priceData.closeTo52weekslowPct < 10")
    List<Stock> findStocksNear52WeekLow();

    // Near 52-week high (within 10% of high)
    @Query("SELECT s FROM Stock s WHERE s.priceData.closeTo52weekslowPct > 90")
    List<Stock> findStocksNear52WeekHigh();

    // Graham's undervalued (Price < Graham Fair Value)
    @Query("SELECT s FROM Stock s WHERE s.priceData.lastPrice < s.calculatedValues.grahamFairValue")
    List<Stock> findUndervaluedByGraham();

    // Government owned stocks
    @Query("SELECT s FROM Stock s WHERE s.ownershipType = :ownershipType")
    List<Stock> findByOwnership(@Param("ownershipType") OwnershipType ownershipType);

    // High dividend yield ( > 5%)
    @Query("SELECT s FROM Stock s WHERE s.fundamentalData.dividendYield > 5")
    List<Stock> findHighDividendStocks();

    // Low P/E ratio ( < 10)
    @Query("SELECT s FROM Stock s WHERE s.fundamentalData.peRatio < 10 AND s.fundamentalData.peRatio > 0")
    List<Stock> findLowPERatioStocks();

    // Profitable companies with low debt
    @Query("SELECT s FROM Stock s WHERE s.fundamentalData.eps > 0 AND s.ratiosData.debtToEquity < 50")
    List<Stock> findProfitableLowDebtStocks();

    // Combined filter with multiple conditions
    @Query("SELECT s FROM Stock s WHERE " +
            "(:sector IS NULL OR s.sector = :sector) AND " +
            "(:ownershipType IS NULL OR s.ownershipType = :ownershipType) AND " +
            "(:minPrice IS NULL OR s.priceData.lastPrice >= :minPrice) AND " +
            "(:maxPrice IS NULL OR s.priceData.lastPrice <= :maxPrice) AND " +
            "(:minPe IS NULL OR s.fundamentalData.peRatio >= :minPe) AND " +
            "(:maxPe IS NULL OR s.fundamentalData.peRatio <= :maxPe) AND " +
            "(:minYield IS NULL OR s.fundamentalData.dividendYield >= :minYield)")
    Page<Stock> findStocksByFilters(
            @Param("sector") SectorType sector,
            @Param("ownershipType") OwnershipType ownershipType,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minPe") BigDecimal minPe,
            @Param("maxPe") BigDecimal maxPe,
            @Param("minYield") BigDecimal minYield,
            Pageable pageable);

    // Search by symbol or name
    @Query("SELECT s FROM Stock s WHERE LOWER(s.symbol) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Stock> searchByKeyword(@Param("keyword") String keyword);

    // Recently updated stocks
    List<Stock> findByLastUpdateAfter(LocalDateTime dateTime);

    // Count by sector
    @Query("SELECT s.sector, COUNT(s) FROM Stock s GROUP BY s.sector")
    List<Object[]> countStocksBySector();

    // Count by ownership type
    @Query("SELECT s.ownershipType, COUNT(s) FROM Stock s GROUP BY s.ownershipType")
    List<Object[]> countStocksByOwnership();

    // Average P/E by sector
    @Query("SELECT s.sector, AVG(s.fundamentalData.peRatio) FROM Stock s WHERE s.fundamentalData.peRatio IS NOT NULL GROUP BY s.sector")
    List<Object[]> averagePeRatioBySector();

    // Average dividend yield by sector
    @Query("SELECT s.sector, AVG(s.fundamentalData.dividendYield) FROM Stock s WHERE s.fundamentalData.dividendYield IS NOT NULL GROUP BY s.sector")
    List<Object[]> averageDividendYieldBySector();

    // Top gainers
    @Query("SELECT s FROM Stock s WHERE s.priceData.changePct IS NOT NULL ORDER BY s.priceData.changePct DESC")
    Page<Stock> findTopGainers(Pageable pageable);

    // Top losers
    @Query("SELECT s FROM Stock s WHERE s.priceData.changePct IS NOT NULL ORDER BY s.priceData.changePct ASC")
    Page<Stock> findTopLosers(Pageable pageable);

    // Most active by volume
    @Query("SELECT s FROM Stock s WHERE s.volumeData.volume IS NOT NULL ORDER BY s.volumeData.volume DESC")
    Page<Stock> findMostActive(Pageable pageable);

    // Largest market cap
    @Query("SELECT s FROM Stock s WHERE s.fundamentalData.marketCap IS NOT NULL ORDER BY s.fundamentalData.marketCap DESC")
    Page<Stock> findLargestMarketCap(Pageable pageable);

    // Check if stock exists
    boolean existsBySymbol(String symbol);

    // Delete by symbol
    void deleteBySymbol(String symbol);
}