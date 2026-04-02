package com.tunindex.market_tool.core.webscraping;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SimpleCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${market-tool.cache.ttl:3600}")
    private int defaultTtlSeconds;

    /**
     * Cache entry with value and expiry time
     */
    private static class CacheEntry {
        private final Object value;
        private final long expiryTime;

        public CacheEntry(Object value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }

        public Object getValue() {
            return value;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    /**
     * Retrieve a value from cache if not expired
     * Maps to Python: simple_cache.py - get(key)
     *
     * Python equivalent:
     * def get(key):
     *     if key in CACHE:
     *         value, expiry = CACHE[key]
     *         if expiry > time.time():
     *             return value
     *     return None
     */
    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            log.debug("Cache hit for key: {}", key);
            return entry.getValue();
        }

        if (entry != null && entry.isExpired()) {
            log.debug("Cache expired for key: {}", key);
            cache.remove(key);
        }

        log.debug("Cache miss for key: {}", key);
        return null;
    }

    /**
     * Retrieve value with type casting
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value != null && type.isAssignableFrom(value.getClass())) {
            return (T) value;
        }
        return null;
    }

    /**
     * Save a value in cache for ttl seconds
     * Maps to Python: simple_cache.py - set(key, value, ttl=3600)
     *
     * Python equivalent:
     * def set(key, value, ttl=3600):
     *     CACHE[key] = (value, time.time() + ttl)
     */
    public void set(String key, Object value) {
        set(key, value, defaultTtlSeconds);
    }

    public void set(String key, Object value, int ttlSeconds) {
        long expiryTime = System.currentTimeMillis() + (ttlSeconds * 1000L);
        cache.put(key, new CacheEntry(value, expiryTime));
        log.debug("Cached key: {} with TTL: {} seconds", key, ttlSeconds);
    }

    /**
     * Check if key exists and is not expired
     */
    public boolean containsKey(String key) {
        CacheEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Remove a key from cache
     */
    public void remove(String key) {
        cache.remove(key);
        log.debug("Removed key from cache: {}", key);
    }

    /**
     * Clear all cache entries
     */
    public void clear() {
        cache.clear();
        log.info("Cache cleared");
    }

    /**
     * Get cache size (number of entries)
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clean expired entries
     */
    public void cleanExpired() {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int after = cache.size();
        if (before > after) {
            log.debug("Cleaned {} expired cache entries", before - after);
        }
    }

    /**
     * Get remaining TTL for a key in seconds
     */
    public long getRemainingTtl(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return 0;
        }
        return (entry.getExpiryTime() - System.currentTimeMillis()) / 1000;
    }

    /**
     * Update TTL for existing key
     */
    public boolean updateTtl(String key, int newTtlSeconds) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            long newExpiryTime = System.currentTimeMillis() + (newTtlSeconds * 1000L);
            cache.put(key, new CacheEntry(entry.getValue(), newExpiryTime));
            log.debug("Updated TTL for key: {} to {} seconds", key, newTtlSeconds);
            return true;
        }
        return false;
    }

    /**
     * Get all keys in cache
     */
    public java.util.Set<String> getKeys() {
        return new java.util.HashSet<>(cache.keySet());
    }

    /**
     * Scheduled cleanup of expired entries
     */
    @PostConstruct
    public void startCleanupScheduler() {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::cleanExpired, 60, 60, java.util.concurrent.TimeUnit.SECONDS);
        log.info("Cache cleanup scheduler started (every 60 seconds)");
    }
}