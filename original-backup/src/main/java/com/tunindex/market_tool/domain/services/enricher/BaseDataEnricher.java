package com.tunindex.market_tool.domain.services.enricher;

import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.domain.services.calculator.GrahamCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
@Slf4j
public abstract class BaseDataEnricher implements DataEnricherService {

    protected final GrahamCalculator grahamCalculator;

    @Override
    public BigDecimal calculateBvps(Stock stock) {
        BigDecimal totalEquity = null;
        Long sharesOutstanding = null;

        if (stock.getFundamentalData() != null) {
            sharesOutstanding = stock.getFundamentalData().getSharesOutstanding();
        }

        // If we already have BVPS, return it
        if (stock.getCalculatedValues() != null) {
            BigDecimal bvps = stock.getCalculatedValues().getBookValuePerShare();
            if (bvps != null) {
                return bvps;
            }
        }

        // For now, return null if not available
        // You can implement total equity calculation if you have that data
        if (totalEquity != null && sharesOutstanding != null && sharesOutstanding > 0) {
            BigDecimal sharesBD = BigDecimal.valueOf(sharesOutstanding);
            return totalEquity.divide(sharesBD, 2, RoundingMode.HALF_UP);
        }

        return null;
    }

    protected void setCalculatedValues(Stock stock, BigDecimal fairValue, BigDecimal marginOfSafety, BigDecimal bvps) {
        CalculatedValues calculatedValues = stock.getCalculatedValues();
        if (calculatedValues == null) {
            calculatedValues = new CalculatedValues();
            stock.setCalculatedValues(calculatedValues);
        }

        if (fairValue != null) {
            calculatedValues.setGrahamFairValue(fairValue);
            log.debug("Set Graham Fair Value: {}", fairValue);
        }

        if (marginOfSafety != null) {
            calculatedValues.setMarginOfSafety(marginOfSafety);
            log.debug("Set Margin of Safety: {}%", marginOfSafety);
        }

        if (bvps != null && calculatedValues.getBookValuePerShare() == null) {
            calculatedValues.setBookValuePerShare(bvps);
            log.debug("Set Book Value Per Share: {}", bvps);
        }
    }

    protected void set52WeekPosition(Stock stock) {
        if (stock.getPriceData() != null) {
            BigDecimal currentPrice = stock.getPriceData().getLastPrice();
            BigDecimal week52Low = stock.getPriceData().getWeek52Low();
            BigDecimal week52High = stock.getPriceData().getWeek52High();

            if (currentPrice != null && week52Low != null && week52High != null) {
                BigDecimal positionPct = grahamCalculator.calculateCloseTo52WeekLowPercentage(
                        currentPrice, week52Low, week52High
                );
                if (positionPct != null) {
                    stock.getPriceData().setCloseTo52weekslowPct(positionPct);
                    log.debug("Set 52-week position: {}%", positionPct);
                }
            }
        }
    }
}