package com.tunindex.market_tool.core.config.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.PageLoadStrategy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
public class ChromeDriverService {

    private ChromeDriver driver;
    private volatile boolean isHealthy = true;

    public void init() {
        createNewDriver();
    }

    private void createNewDriver() {
        log.info("🚀 Initializing ChromeDriver in HEADLESS mode...");

        // Kill any zombie Chrome processes (Windows)
        try {
            ProcessBuilder pb = new ProcessBuilder("taskkill", "/F", "/IM", "chrome.exe", "/T");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(3, TimeUnit.SECONDS);
            log.debug("Killed existing Chrome processes");
        } catch (Exception e) {
            log.debug("No zombie Chrome processes to kill: {}", e.getMessage());
        }

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        // ========== CRITICAL: Use older Chrome binary if available ==========
        // If you have Chrome 114-120 installed, point to it:
        // options.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");

        // ========== HEADLESS MODE with additional stability ==========
        options.addArguments("--headless=new");
        options.addArguments("--window-size=1920,1080");

        // ========== CRITICAL FIXES FOR CHROME 147 ==========
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-search-engine-choice-screen");
        options.addArguments("--disable-features=VizDisplayCompositor");
        options.addArguments("--disable-features=Translate");

        // ========== MEMORY AND STABILITY ==========
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-software-rasterizer");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-setuid-sandbox");
        options.addArguments("--remote-debugging-port=0");

        // ========== PREVENT CRASHES ==========
        options.addArguments("--disable-crash-reporter");
        options.addArguments("--disable-in-process-stack-traces");
        options.addArguments("--disable-logging");
        options.addArguments("--log-level=3");
        options.addArguments("--silent");
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-renderer-backgrounding");

        // ========== MEMORY LIMITS ==========
        options.addArguments("--max_old_space_size=512");
        options.addArguments("--js-flags=--max-old-space-size=512");

        // ========== STEALTH OPTIONS ==========
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // ========== DISABLE NOTIFICATIONS ==========
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        // ========== USER AGENT ==========
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // ========== PAGE LOAD STRATEGY ==========
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

        // ========== PREFERENCES ==========
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_setting_values.popups", 2);
        options.setExperimentalOption("prefs", prefs);

        try {
            driver = new ChromeDriver(options);

            // Set timeouts
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

            // Test if driver is working
            driver.get("about:blank");
            Thread.sleep(1000);

            executeStealthJavaScript();
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
            log.warn("Driver is unhealthy, attempting to reinitialize...");
            try {
                cleanup();
                createNewDriver();
            } catch (Exception e) {
                log.error("Failed to reinitialize driver: {}", e.getMessage());
                return null;
            }
        }

        log.debug("Fetching URL: {}", url);

        try {
            // Use shorter timeout for initial load
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(25));
            driver.get(url);

            // Wait for page to load
            Thread.sleep(8000);

            // Check if page loaded correctly
            String title = driver.getTitle();
            if (title != null && (title.contains("Access Denied") || title.contains("403") || title.contains("502"))) {
                log.warn("Access denied for URL: {}", url);
                return null;
            }

            String html = driver.getPageSource();

            if (html == null || html.length() < 500) {
                log.warn("Page content too short ({} chars) for URL: {}", html != null ? html.length() : 0, url);
                return null;
            }

            if (html.contains("__NEXT_DATA__")) {
                log.info("✅ Successfully fetched full page with __NEXT_DATA__");
            } else {
                log.debug("Fetched page without __NEXT_DATA__");
            }

            isHealthy = true;
            return html;

        } catch (org.openqa.selenium.TimeoutException e) {
            log.warn("Timeout fetching URL: {}", url);
            isHealthy = false;
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch page: {}", e.getMessage());
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