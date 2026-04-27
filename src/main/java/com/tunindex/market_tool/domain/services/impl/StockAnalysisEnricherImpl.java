package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.EnrichmentException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.domain.entities.embedded.PriceData;
import com.tunindex.market_tool.domain.entities.embedded.RatiosData;
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
                log.info("📊 Enriching StockAnalysis data for: {}", stock.getSymbol());

                // Ensure embedded objects exist
                if (stock.getPriceData() == null) {
                    stock.setPriceData(new PriceData());
                }
                if (stock.getRatiosData() == null) {
                    stock.setRatiosData(new RatiosData());
                }
                if (stock.getCalculatedValues() == null) {
                    stock.setCalculatedValues(new CalculatedValues());
                }

                // ========== 1. GET EPS ==========
                BigDecimal eps = null;
                if (stock.getFundamentalData() != null) {
                    eps = stock.getFundamentalData().getEps();
                    log.info("📈 EPS from FundamentalData: {}", eps);
                }

                // Fallback: Calculate EPS from PE ratio and price
                if (eps == null && stock.getFundamentalData() != null && stock.getPriceData() != null) {
                    BigDecimal peRatio = stock.getFundamentalData().getPeRatio();
                    BigDecimal price = stock.getPriceData().getLastPrice();
                    if (peRatio != null && price != null && peRatio.compareTo(BigDecimal.ZERO) > 0) {
                        eps = price.divide(peRatio, 4, RoundingMode.HALF_UP);
                        log.info("📈 Calculated EPS from PE ratio: {}", eps);
                    }
                }

                // ========== 2. GET BVPS ==========
                BigDecimal bvps = null;
                if (stock.getCalculatedValues() != null) {
                    bvps = stock.getCalculatedValues().getBookValuePerShare();
                    log.info("📚 BVPS from CalculatedValues: {}", bvps);
                }

                // Fallback: Calculate BVPS if missing
                if (bvps == null) {
                    bvps = calculateBvps(stock);
                    log.info("📚 Calculated BVPS: {}", bvps);
                }

                // ========== 3. GET CURRENT PRICE ==========
                BigDecimal price = null;
                if (stock.getPriceData() != null) {
                    price = stock.getPriceData().getLastPrice();
                    log.info("💰 Current Price: {}", price);
                }

                // ========== 4. GET 52-WEEK DATA ==========
                BigDecimal week52Low = null;
                BigDecimal week52High = null;
                if (stock.getPriceData() != null) {
                    week52Low = stock.getPriceData().getWeek52Low();
                    week52High = stock.getPriceData().getWeek52High();
                    log.info("📊 52-Week Low: {}, High: {}", week52Low, week52High);
                }

                // ========== 5. CALCULATE GRAHAM FAIR VALUE ==========
                BigDecimal fairValue = null;
                if (eps != null && bvps != null) {
                    fairValue = grahamCalculator.calculateGrahamFairValue(eps, bvps);
                    log.info("📈 Graham Fair Value: {}", fairValue);
                } else {
                    log.warn("⚠️ Cannot calculate Graham Fair Value - EPS: {}, BVPS: {}", eps, bvps);
                }

                // ========== 6. CALCULATE MARGIN OF SAFETY ==========
                BigDecimal marginOfSafety = null;
                if (price != null && fairValue != null && fairValue.compareTo(BigDecimal.ZERO) > 0) {
                    marginOfSafety = grahamCalculator.calculateMarginOfSafety(price, fairValue);
                    log.info("🛡️ Margin of Safety: {}%", marginOfSafety);
                } else {
                    log.debug("Cannot calculate Margin of Safety - Price: {}, FairValue: {}", price, fairValue);
                }

                // ========== 7. CALCULATE CLOSE TO 52-WEEK LOW PERCENTAGE ==========
                // This is the percentage of how far the current price is above the 52-week low
                // Example: If low=90 and current=146, result is ~62.22% (price is 62.22% above low)
                BigDecimal closeTo52WeekLowPct = null;
                if (price != null && week52Low != null && week52Low.compareTo(BigDecimal.ZERO) > 0) {
                    closeTo52WeekLowPct = grahamCalculator.calculateCloseTo52WeekLowPercentage(price, week52Low, week52High);
                    if (closeTo52WeekLowPct != null) {
                        stock.getPriceData().setCloseTo52weekslowPct(closeTo52WeekLowPct);
                        log.info("📊 Close to 52-Week Low: {}% (price is {}% above low)", closeTo52WeekLowPct, closeTo52WeekLowPct);
                    }
                }

                // ========== 8. CALCULATE DISTANCE FROM 52-WEEK LOW ==========
                // This is the percentage distance from the 52-week low (100% - closeTo52WeekLowPct)
                // Example: 100% - 62.22% = 37.78% away from low
                BigDecimal distanceFrom52WeekLow = null;
                if (closeTo52WeekLowPct != null) {
                    distanceFrom52WeekLow = grahamCalculator.calculateDistanceFrom52WeekLow(price, week52Low, week52High);
                    log.info("📊 Distance from 52-Week Low: {}%", distanceFrom52WeekLow);
                }

                // ========== 9. CALCULATE PRICE TO BOOK RATIO ==========
                BigDecimal priceToBook = null;
                if (price != null && bvps != null && bvps.compareTo(BigDecimal.ZERO) > 0) {
                    priceToBook = price.divide(bvps, 2, RoundingMode.HALF_UP);
                    stock.getRatiosData().setPriceToBook(priceToBook);
                    log.info("📐 Price to Book Ratio: {}", priceToBook);
                }

                // ========== 10. CALCULATE PRICE TO EARNINGS RATIO (if not already set) ==========
                if (stock.getFundamentalData() != null && stock.getFundamentalData().getPeRatio() == null && eps != null && eps.compareTo(BigDecimal.ZERO) > 0 && price != null) {
                    BigDecimal peRatio = price.divide(eps, 2, RoundingMode.HALF_UP);
                    stock.getFundamentalData().setPeRatio(peRatio);
                    log.info("📐 Calculated P/E Ratio: {}", peRatio);
                }

                // ========== 11. SET ALL CALCULATED VALUES ==========
                setCalculatedValues(stock, fairValue, marginOfSafety, bvps);

                // ========== 12. UPDATE 52-WEEK RANGE STRING ==========
                if (stock.getPriceData().getWeek52Range() == null && week52Low != null && week52High != null) {
                    stock.getPriceData().setWeek52Range(week52Low + " - " + week52High);
                    log.info("📊 52-Week Range: {}", stock.getPriceData().getWeek52Range());
                }

                // ========== FINAL LOG SUMMARY ==========
                log.info("========================================");
                log.info("✅ ENRICHMENT COMPLETE FOR: {}", stock.getSymbol());
                log.info("========================================");
                log.info("   💰 Price: {}", price);
                log.info("   📈 EPS: {}", eps);
                log.info("   📚 BVPS: {}", bvps);
                log.info("   📐 P/B Ratio: {}", priceToBook);
                log.info("   📊 Graham Fair Value: {}", fairValue);
                log.info("   🛡️ Margin of Safety: {}%", marginOfSafety);
                log.info("   📉 52-Week Low: {}", week52Low);
                log.info("   📈 52-Week High: {}", week52High);
                log.info("   📊 Close to 52W Low: {}%", closeTo52WeekLowPct);
                log.info("   📊 Distance from 52W Low: {}%", distanceFrom52WeekLow);
                log.info("========================================");

                return new EnrichedStockData(stock);

            } catch (Exception e) {
                log.error("Failed to enrich stock {}: {}", stock.getSymbol(), e.getMessage(), e);
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