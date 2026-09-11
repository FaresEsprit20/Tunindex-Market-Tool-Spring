package com.tunindex.market_tool.api.controllers.analysis;

import com.tunindex.market_tool.api.dto.analysis.FundamentalAnalysisResponseDto;
import com.tunindex.market_tool.api.dto.analysis.TechnicalAnalysisResponseDto;
import com.tunindex.market_tool.api.dto.analyst.TradeSetupResponseDto;
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
            // Bars, not calendar days. For a thinly traded stock the two are
            // very different, and a date window silently returned too few rows
            // to compute anything. The old "days" name is still accepted so an
            // existing caller does not break.
            @RequestParam(value = "bars", defaultValue = "250") int bars);

    @GetMapping(value = APP_ROOT + "/analysis/{symbol}/fundamental", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Sector-relative fundamental scoring computed from real stock data")
    FundamentalAnalysisResponseDto fundamental(@PathVariable("symbol") String symbol);

    @GetMapping(value = APP_ROOT + "/analysis/{symbol}/setup", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Tradify Analyst: the buy zone, targets and invalidation level for a stock")
    TradeSetupResponseDto setup(@PathVariable("symbol") String symbol);
}
