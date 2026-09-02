package com.tunindex.market_tool.api.controllers.pipeline;

import com.tunindex.market_tool.common.dto.pipeline.PipelineSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PipelineController implements PipelineApi {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/pipeline";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> start() {
        log.info("API calling Collector to start pipeline");

        try {
            Map<String, Object> body = webClientBuilder.build()
                    .post()
                    .uri(COLLECTOR_URL + "/start")
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return ResponseEntity.accepted().body(body);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.Conflict conflict) {
            return ResponseEntity.status(409).body(Map.of(
                    "started", false,
                    "message", "A pipeline run is already in progress"
            ));
        }
    }

    @Override
    public Flux<PipelineSnapshot> status() {
        log.info("API opening pipeline status stream from Collector");

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/status")
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToFlux(PipelineSnapshot.class)
                // The collector emits at least one snapshot per second (its own
                // heartbeat tick), so a stall this long means the upstream died.
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(e -> {
                    log.warn("Pipeline status stream interrupted: {}", e.getMessage());
                    return Flux.empty();
                });
    }
}
