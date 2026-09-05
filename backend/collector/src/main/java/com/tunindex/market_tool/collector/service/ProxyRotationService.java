package com.tunindex.market_tool.collector.service;

import com.tunindex.market_tool.collector.model.ProxyConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ProxyRotationService {

    private final List<ProxyConfig> proxyPool = new ArrayList<>();
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    @Value("${scraper.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${scraper.proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${scraper.proxy.port:8080}")
    private int proxyPort;

    @PostConstruct
    public void init() {
        initializeProxyPool();
    }

    private void initializeProxyPool() {
        // If proxy is enabled, add the configured proxy
        if (proxyEnabled) {
            proxyPool.add(ProxyConfig.builder()
                .host(proxyHost)
                .port(proxyPort)
                .active(true)
                .build());
            log.info("🔄 Proxy pool initialized with: {}:{}", proxyHost, proxyPort);
        } else {
            // Add default proxy for testing
            proxyPool.add(ProxyConfig.builder()
                .host("127.0.0.1")
                .port(8080)
                .active(true)
                .build());
            log.info("🔄 Proxy pool initialized with default proxy");
        }
    }

    public String getCurrentProxy() {
        if (proxyPool.isEmpty() || !proxyEnabled) {
            return "direct";
        }
        
        // Get the best proxy (highest success rate, active)
        ProxyConfig bestProxy = proxyPool.stream()
            .filter(ProxyConfig::isActive)
            .max((p1, p2) -> Double.compare(p1.getSuccessRate(), p2.getSuccessRate()))
            .orElse(null);
        
        if (bestProxy == null) {
            // If no active proxies, reset all
            proxyPool.forEach(ProxyConfig::resetStats);
            bestProxy = proxyPool.get(0);
        }
        
        log.debug("🔄 Using proxy: {}:{}, success rate: {:.2f}%", 
            bestProxy.getHost(), 
            bestProxy.getPort(), 
            bestProxy.getSuccessRate() * 100);
        
        return bestProxy.getHost() + ":" + bestProxy.getPort();
    }

    public void rotateProxy() {
        if (proxyPool.isEmpty() || !proxyEnabled) {
            return;
        }
        
        int newIndex = ThreadLocalRandom.current().nextInt(proxyPool.size());
        currentIndex.set(newIndex);
        
        ProxyConfig proxy = proxyPool.get(newIndex);
        log.info("🔄 Rotated to proxy: {}:{}", proxy.getHost(), proxy.getPort());
    }

    public void markProxySuccess(String proxyAddress) {
        proxyPool.stream()
            .filter(p -> (p.getHost() + ":" + p.getPort()).equals(proxyAddress))
            .findFirst()
            .ifPresent(ProxyConfig::incrementSuccess);
    }

    public void markProxyFailure(String proxyAddress) {
        proxyPool.stream()
            .filter(p -> (p.getHost() + ":" + p.getPort()).equals(proxyAddress))
            .findFirst()
            .ifPresent(proxy -> {
                proxy.incrementFailure();
                log.warn("⚠️ Proxy {} marked as failed. Active: {}", proxyAddress, proxy.isActive());
            });
    }

    public void addProxy(ProxyConfig proxy) {
        proxyPool.add(proxy);
        log.info("➕ Added new proxy: {}:{}", proxy.getHost(), proxy.getPort());
    }

    public void removeProxy(String proxyAddress) {
        boolean removed = proxyPool.removeIf(p -> 
            (p.getHost() + ":" + p.getPort()).equals(proxyAddress));
        if (removed) {
            log.info("➖ Removed proxy: {}", proxyAddress);
        }
    }

    public List<ProxyConfig> getActiveProxies() {
        return proxyPool.stream()
            .filter(ProxyConfig::isActive)
            .toList();
    }

    public int getProxyCount() {
        return proxyPool.size();
    }

    public boolean isProxyEnabled() {
        return proxyEnabled && !proxyPool.isEmpty();
    }
}
