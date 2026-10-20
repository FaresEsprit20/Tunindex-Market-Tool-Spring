package com.tunindex.market_tool.collector.services.fx;

import com.tunindex.market_tool.collector.dto.exchangerate.CurrencyRateDto;
import com.tunindex.market_tool.collector.dto.exchangerate.ExchangeRateResponseDto;
import com.tunindex.market_tool.collector.providers.fx.ExchangeRateProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private final ExchangeRateProvider provider;

    // Display order: EUR first (Tunisia's dominant trade partner), then
    // other majors, then regional Maghreb/Gulf currencies relevant to BVMT
    // investors.
    private static final Map<String, String> CURATED = new LinkedHashMap<>();

    static {
        CURATED.put("EUR", "Euro");
        CURATED.put("USD", "US Dollar");
        CURATED.put("GBP", "British Pound");
        CURATED.put("CHF", "Swiss Franc");
        CURATED.put("JPY", "Japanese Yen");
        CURATED.put("CAD", "Canadian Dollar");
        CURATED.put("CNY", "Chinese Yuan");
        CURATED.put("AED", "UAE Dirham");
        CURATED.put("SAR", "Saudi Riyal");
        CURATED.put("MAD", "Moroccan Dirham");
        CURATED.put("DZD", "Algerian Dinar");
        CURATED.put("LYD", "Libyan Dinar");
    }

    @Override
    public Mono<ExchangeRateResponseDto> getRates() {
        return provider.fetchRatesInTnd().map(rates -> {
            List<CurrencyRateDto> currencyRates = CURATED.entrySet().stream()
                    .filter(e -> rates.containsKey(e.getKey()))
                    .map(e -> CurrencyRateDto.builder()
                            .code(e.getKey())
                            .name(e.getValue())
                            .rateToTnd(rates.get(e.getKey()))
                            .build())
                    .toList();

            return ExchangeRateResponseDto.builder()
                    .baseCurrency("TND")
                    .rates(currencyRates)
                    .lastUpdated(LocalDateTime.now())
                    .build();
        });
    }
}
