package com.tunindex.market_tool.collector.services.history;

import com.tunindex.market_tool.collector.dto.history.PriceHistoryPointDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaHistoryProvider;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryServiceImpl implements PriceHistoryService {

    private final IlBoursaHistoryProvider ilBoursaHistoryProvider;
    private final PriceHistoryRepository priceHistoryRepository;

    @Override
    public Mono<List<PriceHistoryPointDto>> refreshAndGet(String symbol, LocalDate from, LocalDate to) {
        return ilBoursaHistoryProvider.fetchHistory(symbol, from, to)
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(points -> upsert(symbol, points))
                .then(Mono.fromCallable(() -> getStored(symbol, from)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceHistoryPointDto> getStored(String symbol, LocalDate from) {
        return priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(symbol, from)
                .stream()
                .map(PriceHistoryPointDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<BigDecimal>> getClosesForSymbols(List<String> symbols, LocalDate from) {
        // The query returns every symbol's rows already ordered by symbol
        // then date, so grouping preserves chronological order per series
        // without a second sort.
        return priceHistoryRepository
                .findBySymbolInAndTradeDateGreaterThanEqualOrderBySymbolAscTradeDateAsc(symbols, from)
                .stream()
                .filter(point -> point.getClose() != null)
                .collect(Collectors.groupingBy(
                        PriceHistory::getSymbol,
                        LinkedHashMap::new,
                        Collectors.mapping(PriceHistory::getClose, Collectors.toList())));
    }

    // Not @Transactional: this is called via `this.` from within the same
    // class (self-invocation bypasses Spring's proxy-based AOP entirely, so
    // the annotation would be silently ignored anyway). Each repository
    // save() below is already transactional on its own — same as
    // DataOrchestratorImpl.saveOrUpdateStock's per-record upsert.
    private void upsert(String symbol, List<IlBoursaHistoryProvider.PricePoint> points) {
        if (points.isEmpty()) {
            log.debug("No history points returned for {}", symbol);
            return;
        }

        int saved = 0;
        for (IlBoursaHistoryProvider.PricePoint p : points) {
            PriceHistory entity = priceHistoryRepository.findBySymbolAndTradeDate(symbol, p.tradeDate())
                    .orElseGet(() -> PriceHistory.builder().symbol(symbol).tradeDate(p.tradeDate()).build());

            entity.setOpen(p.open());
            entity.setHigh(p.high());
            entity.setLow(p.low());
            entity.setClose(p.close());
            entity.setVolume(p.volume());
            priceHistoryRepository.save(entity);
            saved++;
        }
        log.info("📈 Upserted {} history points for {}", saved, symbol);
    }
}
