package com.tunindex.market_tool.api.entities.enums;

import lombok.Getter;

@Getter
public enum SectorType {
    FINANCIALS("Financials"),
    BANKING("Banking Services"),
    TECHNOLOGY("Technology"),
    INDUSTRIALS("Industrials"),
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