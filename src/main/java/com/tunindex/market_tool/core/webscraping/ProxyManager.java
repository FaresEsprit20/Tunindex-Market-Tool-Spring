package com.tunindex.market_tool.core.webscraping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ProxyManager {

    @Value("${market-tool.scraping.use-proxy:false}")
    private boolean proxyEnabled;

    @Value("${market-tool.proxy.auto-load:true}")
    private boolean autoLoadProxies;

    @Value("${market-tool.proxy.api-url:https://www.proxy-list.download/api/v1/get?type=http}")
    private String proxyApiUrl;

    private final List<String> proxyList = new CopyOnWriteArrayList<>();
    private final Random random = new Random();
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (proxyEnabled && autoLoadProxies) {
            loadProxiesAsync();
        } else if (proxyEnabled) {
            loadProxiesFromFile();
        } else {
            log.info("Proxy is disabled");
        }
    }

    /**
     * Load proxies from API
     * Maps to Python: proxy_manager.py - load_proxies()
     */
    public void loadProxies() {
        if (!proxyEnabled) {
            log.debug("Proxy is disabled, skipping load");
            return;
        }

        if (isLoading.get()) {
            log.debug("Proxy loading already in progress");
            return;
        }

        isLoading.set(true);

        try {
            log.info("Loading proxies from API: {}", proxyApiUrl);

            WebClient webClient = WebClient.builder()
                    .baseUrl(proxyApiUrl)
                    .build();

            String response = webClient.get()
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && !response.isEmpty()) {
                parseProxyResponse(response);
                log.info("Loaded {} proxies", proxyList.size());
            } else {
                log.warn("Empty response from proxy API");
                loadProxiesFromFile();
            }

        } catch (Exception e) {
            log.error("Failed to load proxies from API: {}", e.getMessage());
            loadProxiesFromFile();
        } finally {
            isLoading.set(false);
        }
    }

    /**
     * Load proxies asynchronously
     */
    public void loadProxiesAsync() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait for application startup
                loadProxies();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Load proxies from local file (fallback)
     */
    private void loadProxiesFromFile() {
        try (var inputStream = getClass().getResourceAsStream("/stealth/proxy-list.txt")) {
            if (inputStream != null) {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(inputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            proxyList.add(line);
                        }
                    }
                    log.info("Loaded {} proxies from file", proxyList.size());
                }
            } else {
                log.warn("Proxy list file not found");
            }
        } catch (Exception e) {
            log.error("Failed to load proxies from file: {}", e.getMessage());
        }
    }

    /**
     * Parse proxy API response
     */
    private void parseProxyResponse(String response) {
        try {
            // Try to parse as JSON first
            if (response.trim().startsWith("{")) {
                JsonNode json = objectMapper.readTree(response);
                if (json.has("LISTA")) {
                    String[] proxies = json.get("LISTA").asText().split("\n");
                    for (String proxy : proxies) {
                        proxy = proxy.trim();
                        if (!proxy.isEmpty()) {
                            proxyList.add(proxy);
                        }
                    }
                }
            } else {
                // Parse as plain text (one proxy per line)
                String[] lines = response.split("\n");
                for (String line : lines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        proxyList.add(line);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse proxy response as JSON, treating as plain text");
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    proxyList.add(line);
                }
            }
        }
    }

    /**
     * Get random proxy
     * Maps to Python: proxy_manager.py - get_random_proxy()
     */
    public String getRandomProxy() {
        if (!proxyEnabled) {
            return null;
        }

        if (proxyList.isEmpty()) {
            log.debug("Proxy list is empty, attempting to load");
            loadProxies();
        }

        if (proxyList.isEmpty()) {
            log.warn("No proxies available");
            return null;
        }

        String proxy = proxyList.get(random.nextInt(proxyList.size()));
        log.debug("Selected proxy: {}", proxy);
        return proxy;
    }

    /**
     * Check if proxy is available
     */
    public boolean hasProxy() {
        return proxyEnabled && !proxyList.isEmpty();
    }

    /**
     * Add proxy manually
     */
    public void addProxy(String proxy) {
        if (proxy != null && !proxy.isEmpty()) {
            proxyList.add(proxy);
            log.debug("Added proxy: {}", proxy);
        }
    }

    /**
     * Remove proxy
     */
    public void removeProxy(String proxy) {
        proxyList.remove(proxy);
        log.debug("Removed proxy: {}", proxy);
    }

    /**
     * Get all proxies
     */
    public List<String> getAllProxies() {
        return new ArrayList<>(proxyList);
    }

    /**
     * Get proxy count
     */
    public int getProxyCount() {
        return proxyList.size();
    }

    /**
     * Clear all proxies
     */
    public void clearProxies() {
        proxyList.clear();
        log.info("Cleared all proxies");
    }

    /**
     * Reload proxies
     */
    public void reloadProxies() {
        clearProxies();
        loadProxies();
    }

    /**
     * Validate a proxy by testing it
     * Fixed: Correct proxy configuration syntax
     */
    public boolean validateProxy(String proxy, String testUrl) {
        try {
            String[] parts = proxy.split(":");
            if (parts.length < 2) {
                return false;
            }

            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            // Create HttpClient with proxy
            HttpClient httpClient = HttpClient.create()
                    .proxy(proxySpec -> proxySpec
                            .type(reactor.netty.transport.ProxyProvider.Proxy.HTTP)
                            .host(host)
                            .port(port))
                    .responseTimeout(Duration.ofSeconds(5));

            WebClient webClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();

            String response = webClient.get()
                    .uri(testUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            return response != null && !response.isEmpty();

        } catch (Exception e) {
            log.debug("Proxy validation failed for {}: {}", proxy, e.getMessage());
            return false;
        }
    }
}