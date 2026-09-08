package com.tunindex.market_tool.api.dto.macro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API-facing mirror of the collector's MarketQuoteDto: one traded instrument
 * outside the BVMT — a metal, a crypto pair, or a currency cross.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketQuoteResponseDto {

    /** GOLD | SILVER | BTC | ETH | SOL | USD_TND | EUR_TND */
    private String key;

    private String label;

    /** The provider's ticker, so a figure can be traced back. */
    private String symbol;

    private BigDecimal price;
    private BigDecimal previousClose;
    private BigDecimal changePct;
    private BigDecimal changeValue;
    private String currency;

    /** METAL | CRYPTO | FX */
    private String category;

    /**
     * PREVIOUS_CLOSE | ROLLING_24H — what the change is measured against.
     * Carried through because metals and currencies have a daily close while
     * crypto trades continuously, and the two are not the same measurement.
     */
    private String changeBasis;

    private LocalDateTime fetchedAt;
}
