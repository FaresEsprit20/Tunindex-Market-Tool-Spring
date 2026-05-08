package com.tunindex.market_tool.collector.webscraping;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class BrowserFingerprintGenerator {

    private final Random random = new Random();
    private final List<String> platforms = new ArrayList<>();
    private final List<String> secChUaList = new ArrayList<>();
    private final List<String> acceptLanguages = new ArrayList<>();

    @PostConstruct
    public void init() {
        // Platforms
        platforms.add("Windows");
        platforms.add("macOS");
        platforms.add("Linux");

        // Sec-CH-UA values (Chrome versions)
        secChUaList.add("\"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\", \"Not?A_Brand\";v=\"99\"");
        secChUaList.add("\"Chromium\";v=\"119\", \"Google Chrome\";v=\"119\", \"Not?A_Brand\";v=\"99\"");
        secChUaList.add("\"Chromium\";v=\"118\", \"Google Chrome\";v=\"118\", \"Not?A_Brand\";v=\"99\"");
        secChUaList.add("\"Chromium\";v=\"121\", \"Google Chrome\";v=\"121\", \"Not?A_Brand\";v=\"99\"");
        secChUaList.add("\"Chromium\";v=\"122\", \"Google Chrome\";v=\"122\", \"Not?A_Brand\";v=\"99\"");
        secChUaList.add("\"Chromium\";v=\"123\", \"Google Chrome\";v=\"123\", \"Not?A_Brand\";v=\"99\"");

        // Accept Languages
        acceptLanguages.add("en-US,en;q=0.9");
        acceptLanguages.add("en-GB,en;q=0.9");
        acceptLanguages.add("fr-FR,fr;q=0.9");
        acceptLanguages.add("de-DE,de;q=0.9");
        acceptLanguages.add("it-IT,it;q=0.9");
        acceptLanguages.add("es-ES,es;q=0.9");
        acceptLanguages.add("en-US,en;q=0.9,fr;q=0.8");
        acceptLanguages.add("en-GB,en;q=0.9,de;q=0.8");

        log.info("BrowserFingerprintGenerator initialized with {} platforms, {} user agents, {} languages",
                platforms.size(), secChUaList.size(), acceptLanguages.size());
    }

    public String getPlatform() {
        return platforms.get(random.nextInt(platforms.size()));
    }

    public String getSecChUa() {
        return secChUaList.get(random.nextInt(secChUaList.size()));
    }

    public String getAcceptLanguage() {
        return acceptLanguages.get(random.nextInt(acceptLanguages.size()));
    }

    /**
     * Get a random screen resolution
     */
    public String getScreenResolution() {
        int[][] resolutions = {
                {1920, 1080}, {1366, 768}, {1536, 864}, {1440, 900},
                {2560, 1440}, {1280, 720}, {1600, 900}, {1024, 768}
        };
        int[] resolution = resolutions[random.nextInt(resolutions.length)];
        return resolution[0] + "x" + resolution[1];
    }

    /**
     * Get random timezone
     */
    public String getTimezone() {
        String[] timezones = {
                "America/New_York", "America/Los_Angeles", "Europe/London",
                "Europe/Paris", "Asia/Tokyo", "Australia/Sydney",
                "America/Chicago", "America/Toronto"
        };
        return timezones[random.nextInt(timezones.length)];
    }

    /**
     * Get random color depth
     */
    public int getColorDepth() {
        int[] depths = {24, 30, 32};
        return depths[random.nextInt(depths.length)];
    }
}