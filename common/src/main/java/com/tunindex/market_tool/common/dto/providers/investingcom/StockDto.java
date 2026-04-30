package com.tunindex.market_tool.common.dto.providers.investingcom;

import com.tunindex.market_tool.common.entities.Stock;
import com.tunindex.market_tool.common.entities.enums.OwnershipType;
import com.tunindex.market_tool.common.entities.enums.SectorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDto {

    private Long id;
    private String symbol;
    private String name;
    private String url;
    private String exchange;
    private String exchangeFullName;
    private String market;
    private String currency;
    private SectorType sector;
    private String industry;
    private OwnershipType ownershipType;

    // Price Data
    private BigDecimal lastPrice;
    private BigDecimal prevClose;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private BigDecimal week52High;
    private BigDecimal week52Low;
    private String week52Range;
    private BigDecimal closeTo52weekslowPct;

    // Volume Data
    private Long volume;
    private Long avgVolume3m;

    // Fundamental Data
    private BigDecimal marketCap;
    private Long sharesOutstanding;
    private BigDecimal eps;
    private BigDecimal peRatio;
    private BigDecimal dividendYield;
    private BigDecimal revenue;
    private BigDecimal oneYearReturn;

    // Ratios Data
    private BigDecimal priceToBook;
    private BigDecimal debtToEquity;
    private BigDecimal profitMargin;
    private BigDecimal payoutRatio;

    // Technical Data
    private BigDecimal beta;

    // Calculated Values
    private BigDecimal grahamFairValue;
    private BigDecimal marginOfSafety;
    private BigDecimal bookValuePerShare;

    // Timestamps
    private LocalDateTime lastUpdate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StockDto fromEntity(Stock stock) {
        if (stock == null) {
            return null;
        }

        StockDtoBuilder builder = StockDto.builder()
                .id(stock.getId())
                .symbol(stock.getSymbol())
                .name(stock.getName())
                .url(stock.getUrl())
                .exchange(stock.getExchange())
                .exchangeFullName(stock.getExchangeFullName())
                .market(stock.getMarket())
                .currency(stock.getCurrency())
                .sector(stock.getSector())
                .industry(stock.getIndustry())
                .ownershipType(stock.getOwnershipType())
                .lastUpdate(stock.getLastUpdate())
                .createdAt(stock.getCreatedAt())
                .updatedAt(stock.getUpdatedAt());

        // Price Data
        if (stock.getPriceData() != null) {
            builder.lastPrice(stock.getPriceData().getLastPrice())
                    .prevClose(stock.getPriceData().getPrevClose())
                    .dayHigh(stock.getPriceData().getDayHigh())
                    .dayLow(stock.getPriceData().getDayLow())
                    .week52High(stock.getPriceData().getWeek52High())
                    .week52Low(stock.getPriceData().getWeek52Low())
                    .week52Range(stock.getPriceData().getWeek52Range())
                    .closeTo52weekslowPct(stock.getPriceData().getCloseTo52weekslowPct());
        }

        // Volume Data
        if (stock.getVolumeData() != null) {
            builder.volume(stock.getVolumeData().getVolume())
                    .avgVolume3m(stock.getVolumeData().getAvgVolume3m());
        }

        // Fundamental Data
        if (stock.getFundamentalData() != null) {
            builder.marketCap(stock.getFundamentalData().getMarketCap())
                    .sharesOutstanding(stock.getFundamentalData().getSharesOutstanding())
                    .eps(stock.getFundamentalData().getEps())
                    .peRatio(stock.getFundamentalData().getPeRatio())
                    .dividendYield(stock.getFundamentalData().getDividendYield())
                    .revenue(stock.getFundamentalData().getRevenue())
                    .oneYearReturn(stock.getFundamentalData().getOneYearReturn());
        }

        // Ratios Data
        if (stock.getRatiosData() != null) {
            builder.priceToBook(stock.getRatiosData().getPriceToBook())
                    .debtToEquity(stock.getRatiosData().getDebtToEquity())
                    .profitMargin(stock.getRatiosData().getProfitMargin());
        }

        // Technical Data
        if (stock.getTechnicalData() != null) {
            builder.beta(stock.getTechnicalData().getBeta());
        }

        // Calculated Values
        if (stock.getCalculatedValues() != null) {
            builder.grahamFairValue(stock.getCalculatedValues().getGrahamFairValue())
                    .marginOfSafety(stock.getCalculatedValues().getMarginOfSafety())
                    .bookValuePerShare(stock.getCalculatedValues().getBookValuePerShare());
        }

        return builder.build();
    }
}