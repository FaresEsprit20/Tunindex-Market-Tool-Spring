package com.tunindex.market_tool.core.config.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
@Component
@Slf4j
public class ChromeDriverService {

    private ChromeDriver driver;

    @PostConstruct
    public void init() {
        log.info("🚀 Initializing ChromeDriver in HEADLESS mode...");

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // ========== HEADLESS MODE (No visible browser window) ==========
        options.addArguments("--headless=new");  // New headless mode (Chrome 109+)
        options.addArguments("--window-size=1920,1080");

        // ========== CRITICAL FOR CHROME 147 ==========
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-search-engine-choice-screen");

        // ========== STEALTH OPTIONS ==========
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Disable infobars and notifications
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        // Standard options
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");  // Still needed for some systems

        // User agent for Chrome 147
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");

        // Additional preferences
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        options.setExperimentalOption("prefs", prefs);

        // Create driver
        driver = new ChromeDriver(options);

        // Set timeouts
        driver.manage().timeouts().implicitlyWait(15, TimeUnit.SECONDS);
        driver.manage().timeouts().pageLoadTimeout(60, TimeUnit.SECONDS);

        // Execute stealth JavaScript
        executeStealthJavaScript();

        log.info("✅ ChromeDriver initialized successfully in HEADLESS mode");
    }

    private void executeStealthJavaScript() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
            js.executeScript("window.navigator.chrome = {runtime: {}}");
            js.executeScript("Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]})");
            js.executeScript("Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']})");
            log.debug("Stealth JavaScript executed");
        } catch (Exception e) {
            log.warn("Stealth JavaScript failed: {}", e.getMessage());
        }
    }

    public String fetchPage(String url) {
        log.debug("Fetching URL: {}", url);

        try {
            driver.get(url);

            // Wait for page to load
            Thread.sleep(8000);

            String html = driver.getPageSource();

            if (html.contains("__NEXT_DATA__")) {
                log.info("✅ Successfully fetched full page with __NEXT_DATA__");
            } else {
                log.warn("⚠️ Fetched page without __NEXT_DATA__");
            }

            return html;

        } catch (Exception e) {
            log.error("Failed to fetch page: {}", e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            log.info("Closing ChromeDriver...");
            driver.quit();
        }
    }
}