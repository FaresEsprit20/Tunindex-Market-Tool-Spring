package com.tunindex.market_tool.api.webscraping;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class UserAgentManager {

    @Value("${market-tool.stealth.rotate-user-agent:true}")
    private boolean rotateUserAgent;

    private final List<String> userAgents = new ArrayList<>();
    private final Random random = new Random();

    @PostConstruct
    public void init() {
        // Add User Agents matching Python version
        userAgents.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        userAgents.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");
        userAgents.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/119.0");
        userAgents.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36 Edg/118.0.2088.76");

        userAgents.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        userAgents.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15");
        userAgents.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36");

        userAgents.add("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        userAgents.add("Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/119.0");
        userAgents.add("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36");
        userAgents.add("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
        userAgents.add("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
        userAgents.add("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");
        log.info("Loaded {} user agents", userAgents.size());
    }

    /**
     * Get random user agent
     * Maps to Python: user_agent.py - random_user_agent()
     *
     * Python equivalent:
     * USER_AGENTS = [
     *     "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
     *     "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)",
     *     "Mozilla/5.0 (X11; Linux x86_64)"
     * ]
     *
     * def random_user_agent():
     *     return random.choice(USER_AGENTS)
     */
    public String getRandomUserAgent() {
        if (!rotateUserAgent) {
            return userAgents.get(0);
        }
        return userAgents.get(random.nextInt(userAgents.size()));
    }

    /**
     * Get specific user agent by index
     */
    public String getUserAgentByIndex(int index) {
        if (index >= 0 && index < userAgents.size()) {
            return userAgents.get(index);
        }
        return getRandomUserAgent();
    }

    /**
     * Add custom user agent
     */
    public void addUserAgent(String userAgent) {
        userAgents.add(userAgent);
        log.debug("Added user agent: {}", userAgent);
    }

    /**
     * Get all user agents
     */
    public List<String> getAllUserAgents() {
        return new ArrayList<>(userAgents);
    }

    /**
     * Get user agent count
     */
    public int getUserAgentCount() {
        return userAgents.size();
    }

    /**
     * Check if rotation is enabled
     */
    public boolean isRotationEnabled() {
        return rotateUserAgent;
    }

    /**
     * Enable/disable user agent rotation
     */
    public void setRotationEnabled(boolean enabled) {
        this.rotateUserAgent = enabled;
        log.info("User agent rotation set to: {}", enabled);
    }
}