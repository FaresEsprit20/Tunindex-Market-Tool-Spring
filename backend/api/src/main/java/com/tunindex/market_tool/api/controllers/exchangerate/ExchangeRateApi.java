package com.tunindex.market_tool.api.controllers.exchangerate;

import com.tunindex.market_tool.api.dto.exchangerate.ExchangeRateResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Exchange Rates", description = "Live TND exchange rates against major and regional currencies")
public interface ExchangeRateApi {

    @GetMapping(value = APP_ROOT + "/exchange-rates", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get live TND exchange rates")
    ExchangeRateResponseDto getRates();
}
