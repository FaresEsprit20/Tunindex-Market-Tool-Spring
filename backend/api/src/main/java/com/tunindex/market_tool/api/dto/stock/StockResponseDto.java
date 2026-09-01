package com.tunindex.market_tool.api.dto.stock;

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
public class StockResponseDto {

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
}