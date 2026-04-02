package com.tunindex.market_tool.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "market-tool")
@Data
public class MarketToolProperties {

    // Active Provider
    private ProviderConfig provider = new ProviderConfig();

    // Scraping Settings
    private ScrapingConfig scraping = new ScrapingConfig();

    // Cache Settings
    private CacheConfig cache = new CacheConfig();

    // Parallelism Settings
    private ParallelismConfig parallelism = new ParallelismConfig();

    @Data
    public static class ProviderConfig {
        private String active = "investingcom";
    }

    @Data
    public static class ScrapingConfig {
        private boolean useProxy = false;
        private boolean useBrowser = true;
        private int requestTimeout = 10;
        private int retryCount = 3;
        private long rateLimitDelay = 1500;
    }

    @Data
    public static class CacheConfig {
        private int ttl = 3600; // seconds
    }

    @Data
    public static class ParallelismConfig {
        private int maxWorkers = 10;
        private boolean bvpsSkipIfExists = true;
    }
}