package com.tunindex.market_tool.api.controllers.analysis;

import com.tunindex.market_tool.api.dto.analysis.FundamentalAnalysisResponseDto;
import com.tunindex.market_tool.api.dto.analysis.TechnicalAnalysisResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Analysis", description = "Technical and fundamental analysis computed server-side from real scraped data")
public interface AnalysisApi {

    @GetMapping(value = APP_ROOT + "/analysis/{symbol}/technical", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Technical indicators (SMA, RSI, MACD, Bollinger Bands) computed from real price history")
    TechnicalAnalysisResponseDto technical(
            @PathVariable("symbol") String symbol,
            @RequestParam(value = "days", defaultValue = "180") int days);

    @GetMapping(value = APP_ROOT + "/analysis/{symbol}/fundamental", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sector-relative fundamental scoring computed from real stock data")
    FundamentalAnalysisResponseDto fundamental(@PathVariable("symbol") String symbol);
}
