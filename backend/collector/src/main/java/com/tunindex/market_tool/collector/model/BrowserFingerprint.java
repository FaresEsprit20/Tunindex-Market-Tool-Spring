package com.tunindex.market_tool.collector.model;

import lombok.Builder;
import lombok.Data;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Data
@Builder
public class BrowserFingerprint {
    private String userAgent;
    private String acceptLanguage;
    private String secChUa;
    private String platform;
    private String platformVersion;
    private String arch;
    private String fullVersionList;
    private String saveData;
    private int viewportWidth;
    private String deviceMemory;
    private String downlink;
    private String ect;
    private String rtt;

    public static BrowserFingerprint generate() {
        List<String> userAgents = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        );
        
        List<String> languages = Arrays.asList(
            "en-US,en;q=0.9,fr;q=0.8,de;q=0.7",
            "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7",
            "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7"
        );
        
        List<String> platforms = Arrays.asList("\"Windows\"", "\"macOS\"", "\"Linux\"");
        List<String> versions = Arrays.asList("\"15.0.0\"", "\"14.0.0\"", "\"13.0.0\"");
        List<String> arches = Arrays.asList("\"x64\"", "\"x86\"", "\"arm64\"");
        
        return BrowserFingerprint.builder()
            .userAgent(userAgents.get(ThreadLocalRandom.current().nextInt(userAgents.size())))
            .acceptLanguage(languages.get(ThreadLocalRandom.current().nextInt(languages.size())))
            .secChUa("\"Google Chrome\";v=\"130\", \"Chromium\";v=\"130\", \"Not_A Brand\";v=\"99\"")
            .platform(platforms.get(ThreadLocalRandom.current().nextInt(platforms.size())))
            .platformVersion(versions.get(ThreadLocalRandom.current().nextInt(versions.size())))
            .arch(arches.get(ThreadLocalRandom.current().nextInt(arches.size())))
            .fullVersionList("\"Google Chrome\";v=\"130.0.6723.59\", \"Chromium\";v=\"130.0.6723.59\", \"Not_A Brand\";v=\"99.0.0.0\"")
            .saveData(ThreadLocalRandom.current().nextBoolean() ? "on" : "off")
            .viewportWidth(ThreadLocalRandom.current().nextInt(1366, 1920))
            .deviceMemory(Arrays.asList("4", "8", "16", "32").get(ThreadLocalRandom.current().nextInt(4)))
            .downlink(String.format("%.1f", 5 + ThreadLocalRandom.current().nextDouble(15)))
            .ect(ThreadLocalRandom.current().nextBoolean() ? "4g" : "3g")
            .rtt(String.valueOf(50 + ThreadLocalRandom.current().nextInt(150)))
            .build();
    }
}
