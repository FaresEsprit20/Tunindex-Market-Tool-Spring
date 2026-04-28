package com.tunindex.market_tool.domain.repository.jpa;

import com.tunindex.market_tool.domain.entities.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    // JpaSpecificationExecutor already provides these methods:
    // - List<T> findAll(Specification<T> spec)
    // - Page<T> findAll(Specification<T> spec, Pageable pageable)
    // - long count(Specification<T> spec)

    // ========== SINGLE ENTITY FINDERS (No pagination needed) ==========

    Optional<Stock> findBySymbol(String symbol);

    Optional<Stock> findBySymbolAndExchange(String symbol, String exchange);

    boolean existsBySymbol(String symbol);

    boolean existsBySymbolAndExchange(String symbol, String exchange);

    void deleteBySymbol(String symbol);

    void deleteBySymbolAndExchange(String symbol, String exchange);

    // ========== STATISTICS AGGREGATION (returns raw data, not paginated) ==========

    @Query("SELECT s.sector, COUNT(s) FROM Stock s GROUP BY s.sector")
    List<Object[]> countStocksBySector();

    @Query("SELECT s.ownershipType, COUNT(s) FROM Stock s GROUP BY s.ownershipType")
    List<Object[]> countStocksByOwnership();

    @Query("SELECT s.sector, AVG(s.fundamentalData.peRatio) FROM Stock s WHERE s.fundamentalData.peRatio IS NOT NULL GROUP BY s.sector")
    List<Object[]> averagePeRatioBySector();

    @Query("SELECT s.sector, AVG(s.fundamentalData.dividendYield) FROM Stock s WHERE s.fundamentalData.dividendYield IS NOT NULL GROUP BY s.sector")
    List<Object[]> averageDividendYieldBySector();

    // Update operations
    @Modifying
    @Query("UPDATE Stock s SET s.lastUpdate = CURRENT_TIMESTAMP WHERE s.symbol = :symbol")
    void updateLastUpdateTime(@Param("symbol") String symbol);
}