package com.tunindex.market_tool.api.services.stream;

import com.tunindex.market_tool.api.dto.stock.StockResponseDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Pushes price changes to connected clients so the grid updates without
 * polling from the browser.
 *
 * <h2>One poller, many clients</h2>
 * The upstream fetch runs on a single schedule and fans out to every open
 * emitter. Polling per client would multiply load by the number of open
 * tabs — and a previous version of the alert evaluator taught this codebase
 * what that costs: the connection pool exhausted at ten concurrent holders.
 * Nothing here opens a transaction or touches the database.
 *
 * <h2>Deltas only</h2>
 * Each cycle is diffed against the last snapshot and only changed symbols
 * are sent. On a quiet market that means an empty cycle and no traffic at
 * all, which is the normal case outside the 10:00–14:00 session.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceStreamService {

    private static final String COLLECTOR_URL = "http://collector-service/internal/stock-data";
    private static final long STREAM_TIMEOUT_MS = 10 * 60 * 1000L;

    private final WebClient.Builder webClientBuilder;

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();

    /** symbol -> last price seen, so a cycle can report only what moved. */
    private final Map<String, BigDecimal> lastPrices = new HashMap<>();

    public record PriceTick(String symbol, BigDecimal price, BigDecimal prevClose,
                            BigDecimal changePct, String direction) {}

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitters.add(emitter);

        Runnable drop = () -> emitters.remove(emitter);
        emitter.onCompletion(drop);
        emitter.onTimeout(drop);
        emitter.onError(e -> drop.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("watching", lastPrices.size())));
        } catch (IOException e) {
            drop.run();
        }
        return emitter;
    }

    /**
     * Polls upstream and pushes what changed. Skipped entirely when nobody
     * is listening — an unwatched stream should cost nothing.
     */
    @Scheduled(fixedDelayString = "${market-tool.prices.poll-interval-ms:20000}",
            initialDelayString = "${market-tool.prices.initial-delay-ms:15000}")
    public void pollAndPush() {
        if (emitters.isEmpty()) {
            return;
        }

        List<StockResponseDto> stocks = fetchAll();
        if (stocks.isEmpty()) {
            return;
        }

        List<PriceTick> ticks = new ArrayList<>();
        for (StockResponseDto stock : stocks) {
            BigDecimal price = stock.getLastPrice();
            if (price == null) {
                continue;
            }
            BigDecimal previous = lastPrices.put(stock.getSymbol(), price);
            // A symbol seen for the first time is not a tick — it is the
            // baseline. Reporting it would flash the whole grid on connect.
            if (previous == null || previous.compareTo(price) == 0) {
                continue;
            }
            ticks.add(new PriceTick(
                    stock.getSymbol(),
                    price,
                    stock.getPrevClose(),
                    changePct(price, stock.getPrevClose()),
                    price.compareTo(previous) > 0 ? "up" : "down"));
        }

        if (ticks.isEmpty()) {
            return;
        }

        log.debug("📈 Pushing {} price ticks to {} client(s)", ticks.size(), emitters.size());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("prices").data(ticks));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    private List<StockResponseDto> fetchAll() {
        try {
            PaginationAndFilteringDto request = new PaginationAndFilteringDto();
            request.setPage(1);
            request.setSize(100);
            request.setSortField("symbol");
            request.setSortDirection(SortingDirection.ASC);

            Map<String, Object> body = webClientBuilder.build()
                    .post()
                    .uri(COLLECTOR_URL + "/filter")
                    .header("X-API-Key", internalApiKey)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (body == null || !(body.get("content") instanceof List<?> content)) {
                return List.of();
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            List<StockResponseDto> out = new ArrayList<>();
            for (Object row : content) {
                out.add(mapper.convertValue(row, StockResponseDto.class));
            }
            return out;
        } catch (Exception e) {
            log.warn("Price stream poll failed: {}", e.getMessage());
            return List.of();
        }
    }

    private BigDecimal changePct(BigDecimal price, BigDecimal prevClose) {
        if (prevClose == null || prevClose.signum() == 0) {
            return null;
        }
        return price.subtract(prevClose)
                .divide(prevClose, 6, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
