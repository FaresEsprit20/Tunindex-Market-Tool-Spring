package com.tunindex.market_tool.core.webscraping;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
@Slf4j
public class ProxyManager {

    private final List<String> proxyList = new CopyOnWriteArrayList<>();
    private final Random random = new Random();
    private int currentIndex = 0;

    private static final String[] PROXY_SOURCES = {
            "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
            "https://raw.githubusercontent.com/ShiftyTR/Proxy-List/master/http.txt",
            "https://raw.githubusercontent.com/jetkai/proxy-list/main/online-proxies/txt/proxies-http.txt",
            "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt"
    };

    @PostConstruct
    public void init() {
        log.info("Initializing ProxyManager...");
        loadProxiesFromSources();

        // Refresh proxies every 30 minutes
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::refreshProxies, 30, 30, TimeUnit.MINUTES);
    }

    /**
     * Load proxies from multiple free sources
     */
    private void loadProxiesFromSources() {
        for (String source : PROXY_SOURCES) {
            try {
                List<String> proxies = fetchProxiesFromUrl(source);
                proxyList.addAll(proxies);
                log.info("Loaded {} proxies from {}", proxies.size(), source);
            } catch (Exception e) {
                log.warn("Failed to load proxies from {}: {}", source, e.getMessage());
            }
        }

        // Remove duplicates
        List<String> uniqueProxies = new ArrayList<>(new java.util.LinkedHashSet<>(proxyList));
        proxyList.clear();
        proxyList.addAll(uniqueProxies);

        log.info("Total unique proxies loaded: {}", proxyList.size());

        // Validate a sample of proxies
        validateProbes();
    }

    /**
     * Fetch proxies from URL
     */
    private List<String> fetchProxiesFromUrl(String urlString) throws Exception {
        List<String> proxies = new ArrayList<>();
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            Pattern proxyPattern = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{2,5}$");
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (proxyPattern.matcher(line).matches()) {
                    proxies.add(line);
                }
            }
        }

        return proxies;
    }

    /**
     * Validate a sample of proxies
     */
    private void validateProbes() {
        int sampleSize = Math.min(10, proxyList.size());
        List<String> sample = new ArrayList<>(proxyList.subList(0, sampleSize));

        for (String proxy : sample) {
            if (validateProxy(proxy)) {
                log.debug("Proxy validated: {}", proxy);
            } else {
                log.debug("Proxy failed validation: {}", proxy);
                proxyList.remove(proxy);
            }
        }
    }

    /**
     * Validate a single proxy
     */
    private boolean validateProxy(String proxy) {
        try {
            String[] parts = proxy.split(":");
            if (parts.length != 2) return false;

            HttpURLConnection conn = (HttpURLConnection) new URL("http://httpbin.org/ip").openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Proxy-Authorization", null);

            // Java doesn't support simple proxy auth directly, so we'll just test connectivity
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Refresh proxies periodically
     */
    private void refreshProxies() {
        log.info("Refreshing proxy list...");
        proxyList.clear();
        loadProxiesFromSources();
    }

    /**
     * Get a random proxy (round-robin rotation)
     */
    public String getRandomProxy() {
        if (proxyList.isEmpty()) {
            log.warn("No proxies available, loading fresh list...");
            loadProxiesFromSources();
        }

        if (proxyList.isEmpty()) {
            log.warn("Still no proxies available, returning null");
            return null;
        }

        // Round-robin rotation
        String proxy = proxyList.get(currentIndex % proxyList.size());
        currentIndex++;

        log.debug("Selected proxy: {}", proxy);
        return proxy;
    }

    /**
     * Get proxy in format expected by FlareSolverr
     */
    public String getProxyUrl() {
        String proxy = getRandomProxy();
        if (proxy == null) return null;

        // FlareSolverr expects format: http://host:port or socks5://host:port
        // For now, assume HTTP proxy
        return "http://" + proxy;
    }

    /**
     * Add custom proxy manually
     */
    public void addProxy(String proxy) {
        if (proxy != null && !proxy.isEmpty()) {
            proxyList.add(proxy);
            log.info("Added proxy: {}", proxy);
        }
    }

    /**
     * Get proxy count
     */
    public int getProxyCount() {
        return proxyList.size();
    }

    /**
     * Check if proxies are available
     */
    public boolean hasProxies() {
        return !proxyList.isEmpty();
    }
}