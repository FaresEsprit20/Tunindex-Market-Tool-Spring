package com.tunindex.market_tool.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

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

    // Proxy Settings
    private ProxyConfig proxy = new ProxyConfig();

    // Stealth Settings
    private StealthConfig stealth = new StealthConfig();

    // Retry Settings
    private RetryConfig retry = new RetryConfig();

    // Rate Limit Settings
    private RateLimitConfig rateLimit = new RateLimitConfig();

    // Ownership Settings
    private OwnershipConfig ownership = new OwnershipConfig();

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
        private long retryDelay = 1000;
    }

    @Data
    public static class CacheConfig {
        private int ttl = 3600;
    }

    @Data
    public static class ParallelismConfig {
        private int maxWorkers = 10;
        private boolean bvpsSkipIfExists = true;
    }

    @Data
    public static class ProxyConfig {
        private boolean autoLoad = true;
        private String apiUrl = "https://www.proxy-list.download/api/v1/get?type=http";
        private List<String> list = new ArrayList<>();
        private int validationTimeout = 10000;
    }

    @Data
    public static class StealthConfig {
        private boolean enabled = true;
        private boolean rotateUserAgent = true;
        private boolean rotateFingerprint = true;
        private boolean detectCaptcha = true;
        private String userAgentFile = "stealth/user-agents.txt";
        private String fingerprintFile = "stealth/browser-profiles.json";
    }

    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long initialDelayMs = 1000;
        private long maxDelayMs = 10000;
        private double multiplier = 2.0;
    }

    @Data
    public static class RateLimitConfig {
        private boolean enabled = true;
        private long delayMs = 1500;
        private int requestsPerMinute = 40;
        private int requestsPerHour = 240;
    }

    @Data
    public static class OwnershipConfig {
        private List<String> government = new ArrayList<>();
        private List<String> mixed = new ArrayList<>();
    }
}