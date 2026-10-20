package com.tunindex.market_tool.collector.services.analysis;

import com.tunindex.market_tool.collector.dto.analysis.FundamentalAnalysisDto;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sector-relative fundamental scoring. Every score is a documented,
 * deterministic formula over this stock's own fields plus a live sector
 * average (StockRepository.averageFundamentalsBySector) — not a black-box
 * or a copied third-party rating.
 */
@Component
@RequiredArgsConstructor
public class FundamentalAnalysisCalculator {

    private final StockRepository stockRepository;

    public FundamentalAnalysisDto compute(Stock stock) {
        Object[] sectorAverages = stock.getSector() != null
                ? stockRepository.averageFundamentalsBySector(stock.getSector()).stream().findFirst().orElse(null)
                : null;

        BigDecimal sectorAvgPe = numberAt(sectorAverages, 0);
        BigDecimal sectorAvgDividendYield = numberAt(sectorAverages, 1);
        BigDecimal sectorAvgDebtToEquity = numberAt(sectorAverages, 2);
        BigDecimal sectorAvgProfitMargin = numberAt(sectorAverages, 3);
        BigDecimal sectorAvgPriceToBook = numberAt(sectorAverages, 4);
        int peerCount = sectorAverages != null && sectorAverages[5] != null ? ((Number) sectorAverages[5]).intValue() : 0;

        BigDecimal peRatio = stock.getFundamentalData() != null ? stock.getFundamentalData().getPeRatio() : null;
        BigDecimal dividendYield = stock.getFundamentalData() != null ? stock.getFundamentalData().getDividendYield() : null;
        BigDecimal eps = stock.getFundamentalData() != null ? stock.getFundamentalData().getEps() : null;
        BigDecimal debtToEquity = stock.getRatiosData() != null ? stock.getRatiosData().getDebtToEquity() : null;
        BigDecimal profitMargin = stock.getRatiosData() != null ? stock.getRatiosData().getProfitMargin() : null;
        BigDecimal priceToBook = stock.getRatiosData() != null ? stock.getRatiosData().getPriceToBook() : null;
        BigDecimal marginOfSafety = stock.getCalculatedValues() != null ? stock.getCalculatedValues().getMarginOfSafety() : null;

        int valuationScore = valuationScore(peRatio, sectorAvgPe, marginOfSafety);
        int profitabilityScore = profitabilityScore(profitMargin, sectorAvgProfitMargin, eps);
        int financialHealthScore = financialHealthScore(debtToEquity, sectorAvgDebtToEquity);
        int incomeScore = incomeScore(dividendYield);
        int overallScore = Math.round((valuationScore + profitabilityScore + financialHealthScore + incomeScore) / 4f);

        return FundamentalAnalysisDto.builder()
                .peRatio(peRatio)
                .sectorAvgPeRatio(sectorAvgPe)
                .dividendYield(dividendYield)
                .sectorAvgDividendYield(sectorAvgDividendYield)
                .debtToEquity(debtToEquity)
                .sectorAvgDebtToEquity(sectorAvgDebtToEquity)
                .profitMargin(profitMargin)
                .sectorAvgProfitMargin(sectorAvgProfitMargin)
                .priceToBook(priceToBook)
                .sectorAvgPriceToBook(sectorAvgPriceToBook)
                .sectorPeerCount(peerCount)
                .valuationScore(valuationScore)
                .profitabilityScore(profitabilityScore)
                .financialHealthScore(financialHealthScore)
                .incomeScore(incomeScore)
                .overallScore(overallScore)
                .overallRating(overallScore >= 70 ? "STRONG" : overallScore >= 45 ? "MODERATE" : "WEAK")
                .build();
    }

    /**
     * Cheaper-than-sector P/E and a positive margin of safety both push
     * this up; at sector-average P/E with 0% margin of safety, this lands
     * at 50 (neutral).
     */
    private int valuationScore(BigDecimal peRatio, BigDecimal sectorAvgPe, BigDecimal marginOfSafety) {
        Integer peScore = null;
        if (peRatio != null && sectorAvgPe != null && sectorAvgPe.doubleValue() > 0) {
            double relativeDelta = (peRatio.doubleValue() - sectorAvgPe.doubleValue()) / sectorAvgPe.doubleValue();
            peScore = clamp(100 - relativeDelta * 50);
        }
        Integer mosScore = marginOfSafety != null ? clamp(50 + marginOfSafety.doubleValue()) : null;

        if (peScore != null && mosScore != null) return Math.round((peScore + mosScore) / 2f);
        if (peScore != null) return peScore;
        if (mosScore != null) return mosScore;
        return 50;
    }

    /**
     * Profit margin above the sector average pushes this up; a
     * non-positive EPS (the company is losing money) caps it at 30
     * regardless of margin, since profitability is the whole point of
     * this score.
     */
    private int profitabilityScore(BigDecimal profitMargin, BigDecimal sectorAvgProfitMargin, BigDecimal eps) {
        int score;
        if (profitMargin == null) {
            score = 50;
        } else if (sectorAvgProfitMargin != null) {
            score = clamp(50 + (profitMargin.doubleValue() - sectorAvgProfitMargin.doubleValue()));
        } else {
            score = clamp(profitMargin.doubleValue() * 2);
        }
        if (eps != null && eps.doubleValue() <= 0) {
            score = Math.min(score, 30);
        }
        return score;
    }

    /** Lower debt/equity than the sector average pushes this up (inverse relationship). */
    private int financialHealthScore(BigDecimal debtToEquity, BigDecimal sectorAvgDebtToEquity) {
        if (debtToEquity == null) return 50;
        if (sectorAvgDebtToEquity == null || sectorAvgDebtToEquity.doubleValue() <= 0) {
            // No sector baseline — fall back to an absolute scale (D/E of 2.0 or higher scores 0).
            return clamp(100 - debtToEquity.doubleValue() * 50);
        }
        double relativeDelta = (debtToEquity.doubleValue() - sectorAvgDebtToEquity.doubleValue()) / sectorAvgDebtToEquity.doubleValue();
        return clamp(100 - relativeDelta * 50);
    }

    /** Simple absolute scale: a 10% dividend yield scores 100, 0% scores 0. */
    private int incomeScore(BigDecimal dividendYield) {
        if (dividendYield == null) return 0;
        return clamp(dividendYield.doubleValue() * 10);
    }

    private int clamp(double value) {
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }

    private BigDecimal numberAt(Object[] row, int index) {
        if (row == null || row[index] == null) return null;
        return BigDecimal.valueOf(((Number) row[index]).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }
}
