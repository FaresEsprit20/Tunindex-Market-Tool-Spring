package com.tunindex.market_tool.api.controllers.market;

import com.tunindex.market_tool.api.dto.macro.MacroSnapshotResponseDto;
import com.tunindex.market_tool.api.dto.macro.MarketQuoteResponseDto;
import com.tunindex.market_tool.api.dto.market.MarketBreadthResponseDto;
import com.tunindex.market_tool.api.dto.market.MarketNewsResponseDto;
import com.tunindex.market_tool.api.dto.market.MarketSessionResponseDto;
import com.tunindex.market_tool.api.dto.market.UnusualActivityResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Market", description = "Trading session state and market-wide news")
public class MarketController {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/market";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @GetMapping(value = APP_ROOT + "/market/session", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Current BVMT trading session state, from the published timetable")
    public MarketSessionResponseDto session() {
        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/session")
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(MarketSessionResponseDto.class)
                .timeout(Duration.ofSeconds(15))
                .block();
    }

    @GetMapping(value = APP_ROOT + "/market/news", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Market-wide headlines from the exchange news feed")
    public List<MarketNewsResponseDto> news(@RequestParam(value = "limit", defaultValue = "15") int limit) {
        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/news?limit={limit}", limit)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<MarketNewsResponseDto>>() {})
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    @GetMapping(value = APP_ROOT + "/market/breadth", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Advancers, decliners, top movers and sector performance across the whole market")
    public MarketBreadthResponseDto breadth() {
        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/breadth")
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(MarketBreadthResponseDto.class)
                .timeout(Duration.ofSeconds(15))
                .block();
    }

    @GetMapping(value = APP_ROOT + "/market/unusual", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Names trading unlike themselves today: volume spikes, range breaks, outsized moves")
    public List<UnusualActivityResponseDto> unusual(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/unusual?limit={limit}", limit)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<UnusualActivityResponseDto>>() {})
                .timeout(Duration.ofSeconds(15))
                .block();
    }

    @GetMapping(value = APP_ROOT + "/market/macro", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tunisian policy rates and national accounts — the macro backdrop for the market")
    public MacroSnapshotResponseDto macro() {
        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/macro")
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(MacroSnapshotResponseDto.class)
                // Inside the gateway's own 30s ceiling, not beyond it. This
                // was 45s, which could never succeed: the gateway had already
                // cut the connection and returned 503 fifteen seconds earlier,
                // so the extra wait bought nothing and only held a thread.
                .timeout(Duration.ofSeconds(12))
                // A timeout used to propagate and become a 500, which is the
                // wrong answer twice over: the panel is empty either way, and
                // a 500 tells the client something is broken here rather than
                // that a publisher was slow. An empty snapshot renders as
                // "unavailable", which is both true and what the UI expects.
                .onErrorResume(error -> {
                    log.warn("Macro snapshot unavailable: {}", error.toString());
                    return Mono.just(MacroSnapshotResponseDto.builder()
                            .rates(List.of())
                            .economy(List.of())
                            .currencies(List.of())
                            .unavailable(List.of("Macro data source"))
                            .build());
                })
                .block();
    }

    @GetMapping(value = APP_ROOT + "/market/commodities", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Metals and major crypto pairs, each with its daily change")
    public List<MarketQuoteResponseDto> commodities() {
        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/commodities")
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<MarketQuoteResponseDto>>() {})
                // Was 90s, against a gateway that gives up at 30. A cold cache
                // behind a throttling provider cannot be waited out from here;
                // the collector now declines to queue against a host that is
                // refusing it, so a cold miss comes back quickly rather than
                // slowly.
                .timeout(Duration.ofSeconds(12))
                // An empty list, not a 500. The banner hides itself when there
                // is nothing to show, which is the right outcome when a price
                // provider is unavailable - the rest of the dashboard is fine
                // and should not be taken down with it.
                .onErrorResume(error -> {
                    log.warn("Commodity quotes unavailable: {}", error.toString());
                    return Mono.just(List.<MarketQuoteResponseDto>of());
                })
                .block();
    }
}
