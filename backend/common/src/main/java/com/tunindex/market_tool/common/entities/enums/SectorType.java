package com.tunindex.market_tool.common.entities.enums;

import lombok.Getter;

@Getter
public enum SectorType {
    FINANCIALS("Financials"),
    BANKING("Banking Services"),
    INSURANCE("Insurance"),
    TECHNOLOGY("Technology"),
    INDUSTRIALS("Industrials"),
    MATERIALS("Materials"),
    CONSUMER_GOODS("Consumer Goods"),
    TELECOM("Telecommunications"),
    ENERGY("Energy"),
    HEALTHCARE("Healthcare"),
    REAL_ESTATE("Real Estate"),
    UTILITIES("Utilities"),
    OTHER("Other");

    private final String displayName;

    SectorType(String displayName) {
        this.displayName = displayName;
    }

}