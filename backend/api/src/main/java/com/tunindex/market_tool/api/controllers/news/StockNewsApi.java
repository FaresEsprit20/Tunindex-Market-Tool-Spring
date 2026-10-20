package com.tunindex.market_tool.api.controllers.news;

import com.tunindex.market_tool.api.dto.news.NewsImpactResponseDto;
import com.tunindex.market_tool.api.dto.news.StockNewsResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Stock News", description = "Real per-stock news headlines, scraped from ilboursa.com")
public interface StockNewsApi {

    @GetMapping(value = APP_ROOT + "/news/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a symbol's recent news headlines")
    List<StockNewsResponseDto> get(
            @PathVariable("symbol") String symbol,
            @RequestParam(value = "limit", defaultValue = "20") int limit);

    @GetMapping(value = APP_ROOT + "/news/{symbol}/impact", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a symbol's news paired with a rule-based sentiment tag and the real price move that followed")
    List<NewsImpactResponseDto> getImpact(
            @PathVariable("symbol") String symbol,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "tradingDaysAfter", defaultValue = "3") int tradingDaysAfter);
}
