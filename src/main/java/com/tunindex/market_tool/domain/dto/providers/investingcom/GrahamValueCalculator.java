package com.tunindex.market_tool.domain.dto.providers.investingcom;

import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.entities.embedded.CalculatedValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class GrahamValueCalculator {

    private final FairValueCalculator fairValueCalculator;

    /**
     * Calculate all Graham-related values for a stock
     * @param stock Stock entity (will be modified with calculated values)
     */
    public void calculateAndEnrich(Stock stock) {
        if (stock == null) {
            return;
        }

        // Get required data
        BigDecimal eps = stock.getFundamentalData() != null ? stock.getFundamentalData().getEps() : null;
        BigDecimal bvps = stock.getCalculatedValues() != null ? stock.getCalculatedValues().getBookValuePerShare() : null;
        BigDecimal currentPrice = stock.getPriceData() != null ? stock.getPriceData().getLastPrice() : null;

        // Calculate Graham Fair Value
        BigDecimal grahamValue = fairValueCalculator.grahamFairValue(eps, bvps);

        // Calculate Margin of Safety
        BigDecimal marginOfSafety = null;
        if (grahamValue != null && currentPrice != null) {
            marginOfSafety = fairValueCalculator.marginOfSafety(currentPrice, grahamValue);
        }

        // Create or update CalculatedValues
        CalculatedValues calculatedValues = stock.getCalculatedValues();
        if (calculatedValues == null) {
            calculatedValues = new CalculatedValues();
            stock.setCalculatedValues(calculatedValues);
        }

        calculatedValues.setGrahamFairValue(grahamValue);
        calculatedValues.setMarginOfSafety(marginOfSafety);

        log.debug("Calculated Graham Fair Value for {}: {} (Margin of Safety: {}%)",
                stock.getSymbol(), grahamValue, marginOfSafety);
    }
}