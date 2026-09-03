package com.tunindex.market_tool.collector.repository.jpa;

import com.tunindex.market_tool.collector.entities.PriceHistory;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(String symbol, LocalDate from);

    /**
     * Every listed symbol's closes since a date, in one query. The stock
     * table draws a sparkline per row, and doing that as one request per
     * row would mean 20 round trips to paint a single page.
     */
    List<PriceHistory> findBySymbolInAndTradeDateGreaterThanEqualOrderBySymbolAscTradeDateAsc(
            List<String> symbols, LocalDate from);

    List<PriceHistory> findBySymbolOrderByTradeDateAsc(String symbol);

    Optional<PriceHistory> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    /** Most recent trading day's close on or before a date (e.g. a news publish date). */
    List<PriceHistory> findBySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(String symbol, LocalDate date, Limit limit);

    /** The next N trading days strictly after a date, ascending. */
    List<PriceHistory> findBySymbolAndTradeDateGreaterThanOrderByTradeDateAsc(String symbol, LocalDate date, Limit limit);
}
