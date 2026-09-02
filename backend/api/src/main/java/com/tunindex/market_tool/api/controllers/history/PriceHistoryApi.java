package com.tunindex.market_tool.api.controllers.history;

import com.tunindex.market_tool.api.dto.history.PriceHistoryPointResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Price History", description = "Real daily OHLCV history per symbol, scraped from ilboursa.com")
public interface PriceHistoryApi {

    @GetMapping(value = APP_ROOT + "/history/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a symbol's price history", description = "Returns stored history, fetching fresh data from ilboursa.com the first time or when refresh=true")
    List<PriceHistoryPointResponseDto> get(
            @PathVariable("symbol") String symbol,
            @RequestParam(value = "days", defaultValue = "180") int days,
            @RequestParam(value = "refresh", defaultValue = "false") boolean refresh);
}
