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
import java.util.Random;

@Getter
@Slf4j
public class ChromeDriverService {

    private ChromeDriver driver;
    private volatile boolean isHealthy = true;
    private static final Random random = new Random();

    // Rotating user agents to avoid detection
    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    };

    public void init() {
        createNewDriver();
    }

    private void createNewDriver() {
        log.info("🚀 Initializing ChromeDriver...");

        // Use Chrome 120 (more stable and less detected than 147)
        WebDriverManager.chromedriver()
                .browserVersion("120.0.6099.109")
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

        // VPN compatibility - disable WebRTC to prevent IP leaks
        options.addArguments("--disable-webrtc");
        options.addArguments("--force-webrtc-ip-handling-policy=default_public_interface_only");

        // Disable password manager and autofill
        options.addArguments("--disable-password-manager-reauthentication");

        // Disable background network services that might reveal automation
        options.addArguments("--disable-background-networking");
        options.addArguments("--disable-sync");
        options.addArguments("--disable-default-apps");

        // Anti-detection with random user agent
        String userAgent = USER_AGENTS[random.nextInt(USER_AGENTS.length)];
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=" + userAgent);
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Use EAGER to avoid waiting for all resources
        options.setPageLoadStrategy(PageLoadStrategy.EAGER);

        // Preferences to appear more like a real browser
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_setting_values.images", 1); // Allow images for realistic browsing
        prefs.put("profile.default_content_setting_values.popups", 2);
        prefs.put("profile.default_content_setting_values.geolocation", 2);
        prefs.put("profile.default_content_setting_values.media_stream", 2);

        // Set realistic language preferences
        prefs.put("intl.accept_languages", "en-US,en;q=0.9");
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);

        options.setExperimentalOption("prefs", prefs);

        // Add accept-language header
        options.addArguments("--lang=en-US");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        driver.manage().deleteAllCookies(); // Start with clean cookies

        executeAdvancedStealthJavaScript();
        isHealthy = true;

        log.info("✅ ChromeDriver initialized successfully with user agent: {}", userAgent);
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

            // Wait for body to be present
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Wait for network to be relatively idle
            Thread.sleep(5000);

            // Check if we got blocked (common patterns)
            String pageSource = driver.getPageSource();
            String title = driver.getTitle();

            if (title != null && (title.contains("Access Denied") ||
                    title.contains("403") ||
                    title.contains("Blocked") ||
                    title.contains("Just a moment") ||
                    pageSource.contains("cf-browser-verification") ||
                    pageSource.contains("captcha"))) {
                log.warn("Access blocked detected for URL: {}", url);
                isHealthy = false;
                return null;
            }

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

    private void executeAdvancedStealthJavaScript() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Remove webdriver property
            js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

            // Add chrome property
            js.executeScript("window.navigator.chrome = {runtime: {}}");

            // Modify plugins length to realistic value
            js.executeScript("Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]})");

            // Set realistic languages
            js.executeScript("Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en', 'fr']})");

            // Modify permissions query
            js.executeScript(
                    "const originalQuery = window.navigator.permissions.query;" +
                            "window.navigator.permissions.query = (parameters) => {" +
                            "    if (parameters.name === 'notifications') {" +
                            "        return Promise.resolve({ state: Notification.permission });" +
                            "    }" +
                            "    return originalQuery(parameters);" +
                            "};"
            );

            // Modify webgl vendor
            js.executeScript(
                    "const getParameter = WebGLRenderingContext.prototype.getParameter;" +
                            "WebGLRenderingContext.prototype.getParameter = function(parameter) {" +
                            "    if (parameter === 37445) {" +
                            "        return 'Intel Inc.';" +
                            "    }" +
                            "    if (parameter === 37446) {" +
                            "        return 'Intel Iris OpenGL Engine';" +
                            "    }" +
                            "    return getParameter(parameter);" +
                            "};"
            );

            // Override navigator properties
            js.executeScript(
                    "Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});" +
                            "Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});" +
                            "Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});" +
                            "Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 0});"
            );

            // Add fake Chrome runtime
            js.executeScript(
                    "window.chrome = {" +
                            "    runtime: {}," +
                            "    loadTimes: function() {}," +
                            "    csi: function() {}," +
                            "    app: {}" +
                            "};"
            );

            log.debug("Advanced stealth JavaScript executed successfully");
        } catch (Exception e) {
            log.warn("Advanced stealth JavaScript failed: {}", e.getMessage());
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