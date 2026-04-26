package com.tunindex.market_tool.core.config.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.PageLoadStrategy;

import java.time.Duration;

@Getter
@Slf4j
public class ChromeDriverService {

    private ChromeDriver driver;
    private volatile boolean isHealthy = true;

    public void init() {
        createNewDriver();
    }

    private void createNewDriver() {
        log.info("🚀 Initializing ChromeDriver...");

        // Use specific Chrome version that works with Selenium 4.31.0
        // Chrome 120 is known to work well
        WebDriverManager.chromedriver().browserVersion("120.0.6099.109").setup();

        ChromeOptions options = new ChromeOptions();

        // Essential options for stability
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--remote-allow-origins=*");

        // Disable automation flags
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Page load strategy
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

        // Disable notifications
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");

        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            // Test the driver
            driver.get("about:blank");
            Thread.sleep(1000);

            isHealthy = true;
            log.info("✅ ChromeDriver initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize ChromeDriver: {}", e.getMessage());
            isHealthy = false;
            throw new RuntimeException("ChromeDriver initialization failed", e);
        }
    }

    public String fetchPage(String url) {
        if (!isHealthy || driver == null) {
            log.warn("Driver is unhealthy, reinitializing...");
            cleanup();
            createNewDriver();
        }

        log.debug("Fetching URL: {}", url);

        try {
            driver.get(url);
            Thread.sleep(8000);

            String html = driver.getPageSource();

            if (html == null || html.length() < 500) {
                log.warn("Page content too short for: {}", url);
                return null;
            }

            isHealthy = true;
            return html;

        } catch (Exception e) {
            log.error("Failed to fetch page: {}", e.getMessage());
            isHealthy = false;
            return null;
        }
    }

    public void cleanup() {
        if (driver != null) {
            try {
                log.info("Closing ChromeDriver...");
                driver.quit();
            } catch (Exception e) {
                log.warn("Error closing driver: {}", e.getMessage());
            } finally {
                driver = null;
                isHealthy = false;
            }
        }
    }
}