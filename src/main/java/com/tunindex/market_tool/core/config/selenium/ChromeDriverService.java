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
        log.info("🚀 Initializing ChromeDriver...");

        // Automatically download and setup correct ChromeDriver
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // ========== STEALTH OPTIONS ==========
        // Hide automation flags
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
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        // Random user agent
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36");

        // Additional preferences to mimic real user
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        options.setExperimentalOption("prefs", prefs);

        // Create driver
        driver = new ChromeDriver(options);

        // Set timeouts
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);
        driver.manage().timeouts().pageLoadTimeout(30, TimeUnit.SECONDS);

        // Execute stealth JavaScript to hide webdriver property
        executeStealthJavaScript();

        log.info("✅ ChromeDriver initialized successfully");
    }

    private void executeStealthJavaScript() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
        js.executeScript("window.navigator.chrome = {runtime: {}}");
        js.executeScript("Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]})");
        js.executeScript("Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en']})");
        log.debug("Stealth JavaScript executed");
    }

    public String fetchPage(String url) {
        log.debug("Fetching URL: {}", url);
        driver.get(url);

        // Wait for page to load
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String html = driver.getPageSource();

        if (html.contains("__NEXT_DATA__")) {
            log.info("✅ Successfully fetched full page with __NEXT_DATA__");
        } else {
            log.warn("⚠️ Fetched page without __NEXT_DATA__");
        }

        return html;
    }

    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            log.info("Closing ChromeDriver...");
            driver.quit();
        }
    }
}