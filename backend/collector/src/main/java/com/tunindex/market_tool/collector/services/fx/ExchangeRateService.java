package com.tunindex.market_tool.collector.services.fx;

import com.tunindex.market_tool.collector.dto.exchangerate.ExchangeRateResponseDto;
import reactor.core.publisher.Mono;

public interface ExchangeRateService {
    Mono<ExchangeRateResponseDto> getRates();
}
