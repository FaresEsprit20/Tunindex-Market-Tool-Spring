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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Getter
@Component
@Slf4j
public class ChromeDriverService {

    private ChromeDriver driver;
    private volatile boolean isHealthy = true;

    @PostConstruct
    public void init() {
        createNewDriver();
    }

    private void createNewDriver() {
        log.info("🚀 Initializing ChromeDriver in HEADLESS mode...");

        // Kill any zombie Chrome processes (Windows)
        try {
            Runtime.getRuntime().exec("taskkill /F /IM chrome.exe /T");
            Thread.sleep(2000);
        } catch (Exception e) {
            log.debug("No zombie Chrome processes to kill");
        }

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // HEADLESS MODE with additional stability options
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");

        // CRITICAL: Memory and stability options
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-software-rasterizer");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-setuid-sandbox");
        options.addArguments("--remote-debugging-port=0");
        options.addArguments("--remote-allow-origins=*");

        // Add argument to prevent process from being killed
        options.addArguments("--disable-crash-reporter");
        options.addArguments("--disable-in-process-stack-traces");
        options.addArguments("--disable-logging");
        options.addArguments("--log-level=3");
        options.addArguments("--silent");

        // Memory limits
        options.addArguments("--max_old_space_size=512");
        options.addArguments("--js-flags=--max-old-space-size=512");

        // Stealth options
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Disable notifications and popups
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        // User agent
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");

        // Preferences
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        options.setExperimentalOption("prefs", prefs);

        driver = new ChromeDriver(options);

        // Set timeouts using Duration (Selenium 4+)
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

        executeStealthJavaScript();
        isHealthy = true;

        log.info("✅ ChromeDriver initialized successfully");
    }

    public String fetchPage(String url) {
        if (!isHealthy || driver == null) {
            log.warn("Driver is unhealthy, reinitializing...");
            cleanup();
            createNewDriver();
        }

        log.debug("Fetching URL: {}", url);

        try {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.get(url);
            Thread.sleep(5000);

            // Check if page loaded correctly
            String title = driver.getTitle();
            if (title != null && (title.contains("Access Denied") || title.contains("403"))) {
                throw new RuntimeException("Access denied");
            }

            String html = driver.getPageSource();

            if (html == null || html.length() < 100) {
                throw new RuntimeException("Page too short or empty");
            }

            if (html.contains("__NEXT_DATA__")) {
                log.info("✅ Successfully fetched full page with __NEXT_DATA__");
            } else {
                log.warn("⚠️ Fetched page without __NEXT_DATA__");
            }

            isHealthy = true;
            return html;

        } catch (Exception e) {
            log.error("Failed to fetch page: {}", e.getMessage());
            isHealthy = false;

            // Attempt to recover by restarting driver
            try {
                cleanup();
                createNewDriver();
            } catch (Exception ex) {
                log.error("Failed to restart driver: {}", ex.getMessage());
            }

            return null;
        }
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

    @PreDestroy
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