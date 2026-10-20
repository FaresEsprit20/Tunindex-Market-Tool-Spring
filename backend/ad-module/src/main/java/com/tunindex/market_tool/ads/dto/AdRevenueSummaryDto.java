package com.tunindex.market_tool.ads.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** What one ad has done so far. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdRevenueSummaryDto {

    private Long advertisementId;
    private String name;
    private String pricingModel;
    private BigDecimal revenue;
    private String currency;

    private long impressions;
    private long completedViews;
    private long clicks;
    private long conversions;

    /**
     * Clicks per hundred impressions.
     *
     * <p>Null rather than zero when nothing has been shown yet: a rate needs a
     * denominator, and reporting 0% for an ad that has never run reads as
     * "performing badly" instead of "no data".
     */
    private BigDecimal clickThroughRatePct;
}
