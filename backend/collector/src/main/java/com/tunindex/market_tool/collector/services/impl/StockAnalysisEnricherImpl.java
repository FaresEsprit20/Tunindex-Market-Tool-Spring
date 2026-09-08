package com.tunindex.market_tool.collector.services.impl;

import com.tunindex.market_tool.common.exception.EnrichmentException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.collector.dto.investingcom.EnrichedStockData;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.common.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import com.tunindex.market_tool.common.entities.embedded.RatiosData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import com.tunindex.market_tool.collector.services.calculator.GrahamCalculator;
import com.tunindex.market_tool.collector.services.enricher.BaseDataEnricher;
import com.tunindex.market_tool.collector.services.fundamentals.FundamentalsDeriver;

import java.math.BigDecimal;
import java.util.Collections;

@Service("stockAnalysisEnricher")
@Slf4j
public class StockAnalysisEnricherImpl extends BaseDataEnricher {

    private final FundamentalsDeriver fundamentalsDeriver;

    public StockAnalysisEnricherImpl(GrahamCalculator grahamCalculator,
                                     FundamentalsDeriver fundamentalsDeriver) {
        super(grahamCalculator);
        this.fundamentalsDeriver = fundamentalsDeriver;
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

                // ========== 1. CLOSE THE ARITHMETIC GAPS ==========
                // EPS<->P/E, BVPS<->P/B and the payout ratio are identities, so
                // they live in one place rather than being re-derived per
                // provider. This also persists the derived BVPS: it used to be
                // computed into a local, used for the Graham value and then
                // thrown away, which is why book value read as empty for every
                // stock whose source published only the P/B ratio.
                fundamentalsDeriver.derive(stock);

                BigDecimal eps = stock.getFundamentalData().getEps();
                BigDecimal bvps = stock.getCalculatedValues().getBookValuePerShare();

                // ========== 3. GET CURRENT PRICE ==========
                BigDecimal price = null;
                if (stock.getPriceData() != null) {
                    price = stock.getPriceData().getLastPrice();
                }

                // ========== 4. CALCULATE GRAHAM FAIR VALUE ==========
                BigDecimal fairValue = null;
                if (eps != null && bvps != null) {
                    fairValue = grahamCalculator.calculateGrahamFairValue(eps, bvps);
                    log.info("📈 Graham Fair Value calculated: {}", fairValue);

                    // CRITICAL: Set the fair value to CalculatedValues
                    if (fairValue != null) {
                        stock.getCalculatedValues().setGrahamFairValue(fairValue);
                    }
                } else {
                    log.warn("⚠️ Cannot calculate Graham Fair Value - EPS: {}, BVPS: {}", eps, bvps);
                }

                // ========== 5. CALCULATE MARGIN OF SAFETY ==========
                BigDecimal marginOfSafety = null;
                if (price != null && fairValue != null && fairValue.compareTo(BigDecimal.ZERO) > 0) {
                    marginOfSafety = grahamCalculator.calculateMarginOfSafety(price, fairValue);
                    log.info("🛡️ Margin of Safety calculated: {}%", marginOfSafety);

                    // CRITICAL: Set the margin of safety to CalculatedValues
                    if (marginOfSafety != null) {
                        stock.getCalculatedValues().setMarginOfSafety(marginOfSafety);
                    }
                } else {
                    log.debug("Cannot calculate Margin of Safety - Price: {}, FairValue: {}", price, fairValue);
                }

                // ========== 6. GET 52-WEEK DATA ==========
                BigDecimal week52Low = null;
                BigDecimal week52High = null;
                if (stock.getPriceData() != null) {
                    week52Low = stock.getPriceData().getWeek52Low();
                    week52High = stock.getPriceData().getWeek52High();
                }

                // ========== 7. CALCULATE CLOSE TO 52-WEEK LOW PERCENTAGE ==========
                BigDecimal closeTo52WeekLowPct = null;
                if (price != null && week52Low != null && week52Low.compareTo(BigDecimal.ZERO) > 0) {
                    closeTo52WeekLowPct = grahamCalculator.calculateCloseTo52WeekLowPercentage(price, week52Low, week52High);
                    if (closeTo52WeekLowPct != null) {
                        stock.getPriceData().setCloseTo52weekslowPct(closeTo52WeekLowPct);
                    }
                }

                // Price-to-book is derived alongside the other identities above.

                // ========== 9. UPDATE 52-WEEK RANGE STRING ==========
                if (stock.getPriceData().getWeek52Range() == null && week52Low != null && week52High != null) {
                    stock.getPriceData().setWeek52Range(week52Low + " - " + week52High);
                }

                // Verify values are set
                if (stock.getCalculatedValues().getGrahamFairValue() == null) {
                    log.warn("⚠️ WARNING: Graham Fair Value is NULL after enrichment for {}", stock.getSymbol());
                }
                if (stock.getCalculatedValues().getMarginOfSafety() == null) {
                    log.warn("⚠️ WARNING: Margin of Safety is NULL after enrichment for {}", stock.getSymbol());
                }

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