package com.tunindex.market_tool.api.controllers.pipeline;

import com.tunindex.market_tool.common.dto.pipeline.PipelineSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Pipeline", description = "On-demand control and live status of the collector's scraping pipeline")
public interface PipelineApi {

    @PostMapping(value = APP_ROOT + "/pipeline/start", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Trigger a pipeline run", description = "Starts a scrape of all tracked stocks if one isn't already running")
    ResponseEntity<Map<String, Object>> start();

    @GetMapping(value = APP_ROOT + "/pipeline/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live pipeline status stream", description = "Server-Sent Events stream of live worker/progress state")
    Flux<PipelineSnapshot> status();
}
