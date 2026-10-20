package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.services.status.PipelineRunnerService;
import com.tunindex.market_tool.collector.services.status.PipelineStatusService;
import com.tunindex.market_tool.common.dto.pipeline.PipelineSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/internal/pipeline")
@RequiredArgsConstructor
@Slf4j
public class PipelineController {

    private final PipelineRunnerService runnerService;
    private final PipelineStatusService statusService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        boolean started = runnerService.triggerRun();

        if (!started) {
            return ResponseEntity.status(409).body(Map.of(
                    "started", false,
                    "message", "A pipeline run is already in progress"
            ));
        }

        log.info("▶️ Pipeline run triggered on demand");
        return ResponseEntity.accepted().body(Map.of(
                "started", true,
                "totalStocks", runnerService.totalKnownStocks()
        ));
    }

    @GetMapping("/snapshot")
    public PipelineSnapshot snapshot(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return statusService.currentSnapshot();
    }

    @GetMapping(value = "/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PipelineSnapshot> status(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        // Only ever consumed by the api service's WebClient (server-to-server),
        // never directly by a browser, so a normal header works fine here.
        validateApiKey(apiKey);
        return statusService.stream()
                .distinctUntilChanged()
                .sample(Duration.ofMillis(300));
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("❌ Invalid or missing API key for internal pipeline call");
            throw new SecurityException("Invalid or missing API key");
        }
    }
}
