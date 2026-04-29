package com.tunindex.market_tool.common.dto.providers.investingcom;

import com.tunindex.market_tool.common.entities.Stock;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockDataResponse {

    private boolean success;
    private String message;
    private Stock data;
    private LocalDateTime timestamp;

    public static StockDataResponse success(Stock data) {
        StockDataResponse response = new StockDataResponse();
        response.setSuccess(true);
        response.setMessage("Data retrieved successfully");
        response.setData(data);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }

    public static StockDataResponse error(String message) {
        StockDataResponse response = new StockDataResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
}