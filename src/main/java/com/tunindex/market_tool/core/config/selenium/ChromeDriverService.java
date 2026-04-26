package com.tunindex.market_tool.core.config.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

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

        // Force exact version match for Chrome 147
        WebDriverManager.chromedriver()
                .browserVersion("147")  // match your Chrome major version
                .setup();

        ChromeOptions options = new ChromeOptions();

        // Headless mode
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");

        // Critical stability flags for Windows 11
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-software-rasterizer");
        options.addArguments("--disable-crash-reporter");
        options.addArguments("--disable-in-process-stack-traces");
        options.addArguments("--disable-logging");
        options.addArguments("--log-level=3");
        options.addArguments("--output=/dev/null");
        options.addArguments("--remote-allow-origins=*");

        // Anti-detection
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Use EAGER to avoid waiting for all resources (helps with heavy sites)
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        // Preferences to block heavy resources
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.managed_default_content_settings.images", 2); // Block images
        options.setExperimentalOption("prefs", prefs);

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        // Remove implicitlyWait — it conflicts with explicit waits

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

        try {
            log.info("Navigating to: {}", url);
            driver.get(url);

            // Wait for body to be present rather than a blind sleep
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Additional wait for dynamic content
            Thread.sleep(3000);

            String html = driver.getPageSource();
            isHealthy = true;
            log.info("✅ Fetched {} chars from {}", html.length(), url);
            return html;

        } catch (Exception e) {
            log.error("❌ Failed to fetch page: {}", e.getMessage());
            isHealthy = false;
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