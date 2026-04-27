package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.EnrichmentException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.services.calculator.GrahamCalculator;
import com.tunindex.market_tool.domain.services.enricher.BaseDataEnricher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;

@Service("stockAnalysisEnricher")
@Slf4j
public class StockAnalysisEnricherImpl extends BaseDataEnricher {

    public StockAnalysisEnricherImpl(GrahamCalculator grahamCalculator) {
        super(grahamCalculator);
    }

    @Override
    public Mono<EnrichedStockData> enrich(Stock stock) {
        return Mono.fromCallable(() -> {
            try {
                log.debug("Enriching StockAnalysis data for: {}", stock.getSymbol());

                // Get EPS from FundamentalData (StockAnalysis provides this directly)
                BigDecimal eps = null;
                if (stock.getFundamentalData() != null) {
                    eps = stock.getFundamentalData().getEps();
                }

                // Fallback: Calculate EPS from PE ratio and price
                if (eps == null && stock.getRatiosData() != null && stock.getPriceData() != null) {
                    BigDecimal peRatio = stock.getFundamentalData().getPeRatio();
                    BigDecimal price = stock.getPriceData().getLastPrice();
                    if (peRatio != null && price != null && peRatio.compareTo(BigDecimal.ZERO) > 0) {
                        eps = price.divide(peRatio, 4, RoundingMode.HALF_UP);
                        log.debug("Calculated EPS from PE ratio for {}: {}", stock.getSymbol(), eps);
                    }
                }

                // Get BVPS - StockAnalysis provides this directly in calculated values
                BigDecimal bvps = null;
                if (stock.getCalculatedValues() != null) {
                    bvps = stock.getCalculatedValues().getBookValuePerShare();
                }

                // Fallback: Calculate BVPS if missing
                if (bvps == null) {
                    bvps = calculateBvps(stock);
                    log.debug("Calculated BVPS for {}: {}", stock.getSymbol(), bvps);
                }

                // Get current price
                BigDecimal price = null;
                if (stock.getPriceData() != null) {
                    price = stock.getPriceData().getLastPrice();
                }

                // Calculate Graham Fair Value
                BigDecimal fairValue = grahamCalculator.calculateGrahamFairValue(eps, bvps);

                // Calculate Margin of Safety
                BigDecimal marginOfSafety = null;
                if (price != null && fairValue != null) {
                    marginOfSafety = grahamCalculator.calculateMarginOfSafety(price, fairValue);
                }

                // Set all calculated values
                setCalculatedValues(stock, fairValue, marginOfSafety, bvps);

                // Calculate 52-week position
                set52WeekPosition(stock);

                log.info("Successfully enriched StockAnalysis stock: {} (Fair Value: {}, Margin: {}%, BVPS: {})",
                        stock.getSymbol(), fairValue, marginOfSafety, bvps);

                return new EnrichedStockData(stock);

            } catch (Exception e) {
                log.error("Failed to enrich stock {}: {}", stock.getSymbol(), e.getMessage());
                throw new EnrichmentException(
                        ErrorCodes.ENRICHMENT_ERROR,
                        stock.getSymbol(),
                        "GrahamValue",
                        "Failed to enrich stock: " + e.getMessage(),
                        Collections.singletonList(e.getMessage())
                );
            }
        });
    }
}