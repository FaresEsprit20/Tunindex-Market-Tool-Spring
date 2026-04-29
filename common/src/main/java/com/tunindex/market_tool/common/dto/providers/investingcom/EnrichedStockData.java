package com.tunindex.market_tool.common.dto.providers.investingcom;

import com.tunindex.market_tool.common.entities.Stock;
import lombok.Data;

@Data
public class EnrichedStockData {
    private Stock stock;
    private boolean enriched;
    private String enrichmentMessage;
    private boolean saved;
    private String saveMessage;

    public EnrichedStockData() {
        this.enriched = false;
        this.saved = false;
    }

    public EnrichedStockData(Stock stock) {
        this.stock = stock;
        this.enriched = true;
        this.saved = false;
    }

    public EnrichedStockData(Stock stock, String enrichmentMessage) {
        this.stock = stock;
        this.enriched = true;
        this.enrichmentMessage = enrichmentMessage;
        this.saved = false;
    }
}