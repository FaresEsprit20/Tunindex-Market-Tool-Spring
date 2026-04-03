package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.EnrichmentException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.domain.repository.jpa.StockRepository;
import com.tunindex.market_tool.domain.services.calculator.GrahamCalculator;
import com.tunindex.market_tool.domain.services.enricher.DataEnricherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataEnricherServiceImpl implements DataEnricherService {

    private final GrahamCalculator grahamCalculator;
    private final StockRepository stockRepository;

    @Override
    public Mono<EnrichedStockData> enrich(Stock stock) {
        return Mono.fromCallable(() -> {
            try {
                log.debug("Enriching stock: {}", stock.getSymbol());

                // Get EPS
                BigDecimal eps = null;
                if (stock.getFundamentalData() != null) {
                    eps = stock.getFundamentalData().getEps();
                }

                // Get or calculate BVPS
                BigDecimal bvps = null;
                if (stock.getCalculatedValues() != null) {
                    bvps = stock.getCalculatedValues().getBookValuePerShare();
                }

                // Calculate BVPS if missing (matches Python logic)
                if (bvps == null) {
                    bvps = calculateBvps(stock);
                    if (bvps != null) {
                        if (stock.getCalculatedValues() == null) {
                            stock.setCalculatedValues(new CalculatedValues());
                        }
                        stock.getCalculatedValues().setBookValuePerShare(bvps);
                        log.debug("Calculated BVPS for {}: {}", stock.getSymbol(), bvps);
                    }
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

                // Set calculated values
                CalculatedValues calculatedValues = stock.getCalculatedValues();
                if (calculatedValues == null) {
                    calculatedValues = new CalculatedValues();
                    stock.setCalculatedValues(calculatedValues);
                }
                calculatedValues.setGrahamFairValue(fairValue);
                calculatedValues.setMarginOfSafety(marginOfSafety);

                // Save to database
                Stock savedStock = stockRepository.save(stock);
                log.info("Successfully enriched and saved stock: {} (Fair Value: {}, Margin: {}%)",
                        savedStock.getSymbol(), fairValue, marginOfSafety);

                return new EnrichedStockData(savedStock);

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

    @Override
    public BigDecimal calculateBvps(Stock stock) {
        BigDecimal totalEquity = null;
        Long sharesOutstanding = null;

        // Get shares outstanding
        if (stock.getFundamentalData() != null) {
            sharesOutstanding = stock.getFundamentalData().getSharesOutstanding();
        }

        // Get total equity from balance sheet data (would need to be fetched separately)
        // This is a simplified version - in reality, you'd get this from the balance sheet

        if (totalEquity != null && sharesOutstanding != null && sharesOutstanding > 0) {
            BigDecimal sharesBD = BigDecimal.valueOf(sharesOutstanding);
            return totalEquity.divide(sharesBD, 2, BigDecimal.ROUND_HALF_UP);
        }

        return null;
    }
}