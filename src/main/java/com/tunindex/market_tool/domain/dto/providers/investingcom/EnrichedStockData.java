package com.tunindex.market_tool.domain.dto.providers.investingcom;

import com.tunindex.market_tool.domain.entities.Stock;
import lombok.Data;

@Data
public class EnrichedStockData {
    private Stock stock;
    private boolean enriched;
    private String enrichmentMessage;

    public EnrichedStockData(Stock stock) {
        this.stock = stock;
        this.enriched = true;
    }

    public EnrichedStockData(Stock stock, String enrichmentMessage) {
        this.stock = stock;
        this.enriched = true;
        this.enrichmentMessage = enrichmentMessage;
    }
}