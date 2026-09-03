package com.tunindex.market_tool.collector.services.history;

import com.tunindex.market_tool.collector.dto.history.PriceHistoryPointDto;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

public interface PriceHistoryService {

    /**
     * Fetch history from ilboursa.com for the given symbol and upsert it,
     * then return the full stored series for that symbol.
     */
    Mono<List<PriceHistoryPointDto>> refreshAndGet(String symbol, LocalDate from, LocalDate to);

    /**
     * Just read what's already stored, no fetch.
     */
    List<PriceHistoryPointDto> getStored(String symbol, LocalDate from);

    /**
     * Closing prices per symbol since a date, for many symbols in one query.
     * Feeds the stock table's row sparklines.
     */
    java.util.Map<String, java.util.List<java.math.BigDecimal>> getClosesForSymbols(
            List<String> symbols, LocalDate from);
}
