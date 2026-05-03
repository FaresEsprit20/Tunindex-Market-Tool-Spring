package com.tunindex.market_tool.api.entities.embedded;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalystData {

    private String analystConsensus;
    private Integer analystBuyCount;
    private Integer analystSellCount;
    private Integer analystHoldCount;
}