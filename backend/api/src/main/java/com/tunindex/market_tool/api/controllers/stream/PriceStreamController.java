package com.tunindex.market_tool.api.controllers.stream;

import com.tunindex.market_tool.api.services.stream.PriceStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@RestController
@RequiredArgsConstructor
@Tag(name = "Price stream", description = "Server-sent price changes, pushed as they happen")
public class PriceStreamController {

    private final PriceStreamService priceStreamService;

    @GetMapping(value = APP_ROOT + "/prices/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live stream of price changes across tracked stocks")
    public SseEmitter stream() {
        return priceStreamService.subscribe();
    }
}
