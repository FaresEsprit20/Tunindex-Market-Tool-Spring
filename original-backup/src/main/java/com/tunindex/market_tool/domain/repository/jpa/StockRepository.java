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

    // ========== SINGLE ENTITY FINDERS (For direct lookups by unique fields) ==========

    /**
     * Find a single stock by its unique symbol
     */
    Optional<Stock> findBySymbol(String symbol);

    /**
     * Find a single stock by its unique symbol and exchange combination
     */
    Optional<Stock> findBySymbolAndExchange(String symbol, String exchange);

    // ========== EXISTENCE CHECKS ==========

    /**
     * Check if a stock exists by symbol
     */
    boolean existsBySymbol(String symbol);

    /**
     * Check if a stock exists by symbol and exchange
     */
    boolean existsBySymbolAndExchange(String symbol, String exchange);

    // ========== STATISTICS AGGREGATION (Dashboard/Reporting endpoints) ==========

    /**
     * Count stocks grouped by sector
     * Used for dashboard statistics
     */
    @Query("SELECT s.sector, COUNT(s) FROM Stock s GROUP BY s.sector")
    List<Object[]> countStocksBySector();

    /**
     * Count stocks grouped by ownership type
     * Used for dashboard statistics
     */
    @Query("SELECT s.ownershipType, COUNT(s) FROM Stock s GROUP BY s.ownershipType")
    List<Object[]> countStocksByOwnership();

    // ========== MAINTENANCE OPERATIONS ==========

    /**
     * Update the last update timestamp for a stock
     * Used when refreshing stock data
     */
    @Modifying
    @Query("UPDATE Stock s SET s.updatedAt = CURRENT_TIMESTAMP WHERE s.symbol = :symbol")
    void updateLastUpdateTime(@Param("symbol") String symbol);
}