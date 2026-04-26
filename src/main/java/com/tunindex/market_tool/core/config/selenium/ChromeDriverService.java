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
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
    };

    public void init() {
        createNewDriver();
    }

    private void createNewDriver() {
        log.info("🚀 Initializing ChromeDriver...");

        // Use Chrome 147 to match your installed browser
        WebDriverManager.chromedriver()
                .browserVersion("147")
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
        prefs.put("profile.default_content_setting_values.images", 1);
        prefs.put("profile.default_content_setting_values.popups", 2);
        prefs.put("profile.default_content_setting_values.geolocation", 2);
        prefs.put("profile.default_content_setting_values.media_stream", 2);
        prefs.put("intl.accept_languages", "en-US,en;q=0.9");
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);

        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--lang=en-US");

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        driver.manage().deleteAllCookies();

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

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            Thread.sleep(5000);

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

            js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
            js.executeScript("window.navigator.chrome = {runtime: {}}");
            js.executeScript("Object.defineProperty(navigator, 'plugins', {get: () => [1,2,3,4,5]})");
            js.executeScript("Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en', 'fr']})");

            js.executeScript(
                    "const originalQuery = window.navigator.permissions.query;" +
                            "window.navigator.permissions.query = (parameters) => {" +
                            "    if (parameters.name === 'notifications') {" +
                            "        return Promise.resolve({ state: Notification.permission });" +
                            "    }" +
                            "    return originalQuery(parameters);" +
                            "};"
            );

            js.executeScript(
                    "Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});" +
                            "Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});" +
                            "Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});" +
                            "Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 0});"
            );

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