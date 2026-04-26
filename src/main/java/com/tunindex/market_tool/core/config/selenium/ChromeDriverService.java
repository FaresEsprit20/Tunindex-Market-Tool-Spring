package com.tunindex.market_tool.core.config.selenium;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
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

    // Realistic viewport sizes
    private static final int[][] VIEWPORT_SIZES = {
            {1920, 1080},
            {1366, 768},
            {1536, 864},
            {1440, 900},
            {1280, 720}
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

        // Random viewport size (not always full HD)
        int[] viewport = VIEWPORT_SIZES[random.nextInt(VIEWPORT_SIZES.length)];

        // IMPORTANT: Disable headless temporarily to avoid detection
        // Investing.com blocks headless browsers effectively
        // options.addArguments("--headless=new");  // COMMENTED OUT - USE VISIBLE MODE

        options.addArguments("--window-size=" + viewport[0] + "," + viewport[1]);
        options.addArguments("--start-maximized");

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

        // Use NORMAL page load strategy (wait for all resources)
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

        // Preferences to appear more like a real browser
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_setting_values.images", 1);
        prefs.put("profile.default_content_setting_values.popups", 2);
        prefs.put("profile.default_content_setting_values.geolocation", 2);
        prefs.put("profile.default_content_setting_values.media_stream", 2);
        prefs.put("intl.accept_languages", "en-US,en;q=0.9,fr;q=0.8");
        prefs.put("profile.default_content_setting_values.automatic_downloads", 1);

        // Enable hardware acceleration for better rendering
        prefs.put("profile.hardware_acceleration_mode_enabled", true);

        options.setExperimentalOption("prefs", prefs);
        options.addArguments("--lang=en-US");

        // Disable automation flags
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation", "enable-logging"});

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(90));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(60));
        driver.manage().deleteAllCookies();

        executeAdvancedStealthJavaScript();
        isHealthy = true;

        log.info("✅ ChromeDriver initialized successfully with user agent: {} and viewport: {}x{}", userAgent, viewport[0], viewport[1]);
    }

    public String fetchPage(String url) {
        if (!isHealthy || driver == null) {
            log.warn("Driver is unhealthy, reinitializing...");
            cleanup();
            createNewDriver();
        }

        try {
            // Random delay before navigation (2-6 seconds)
            int preDelay = random.nextInt(4000) + 2000;
            log.debug("Waiting {} ms before navigation", preDelay);
            Thread.sleep(preDelay);

            log.info("Navigating to: {}", url);
            driver.get(url);

            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(45));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            // Random wait for initial content (3-8 seconds)
            int initialWait = random.nextInt(5000) + 3000;
            Thread.sleep(initialWait);

            // Simulate human-like scrolling and mouse movements
            simulateHumanBehavior();

            // Wait additional time for dynamic content
            int dynamicWait = random.nextInt(3000) + 2000;
            Thread.sleep(dynamicWait);

            // Check for blocking
            String pageSource = driver.getPageSource();
            String title = driver.getTitle();

            if (title != null && (title.contains("Access Denied") ||
                    title.contains("403") ||
                    title.contains("Blocked") ||
                    title.contains("Just a moment") ||
                    pageSource.contains("cf-browser-verification") ||
                    pageSource.contains("captcha") ||
                    pageSource.contains("rate limit"))) {
                log.warn("Access blocked detected for URL: {}", url);
                isHealthy = false;
                return null;
            }

            String html = driver.getPageSource();
            isHealthy = true;
            log.info("✅ Fetched {} chars from {}", html.length(), url);

            // Check if we got the full page with data
            if (html.contains("__NEXT_DATA__")) {
                log.info("✅ Full page with __NEXT_DATA__ successfully retrieved");
            } else {
                log.warn("⚠️ Page missing __NEXT_DATA__, likely simplified version");
            }

            return html;

        } catch (Exception e) {
            log.error("❌ Failed to fetch page: {}", e.getMessage());
            isHealthy = false;
            return null;
        }
    }

    private void simulateHumanBehavior() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Actions actions = new Actions(driver);

            // Random scroll to different positions
            int scrollY1 = random.nextInt(500) + 200;
            int scrollY2 = random.nextInt(800) + 500;
            int scrollY3 = random.nextInt(300);

            // Smooth scroll down
            js.executeScript("window.scrollTo({top: " + scrollY1 + ", behavior: 'smooth'});");
            Thread.sleep(random.nextInt(1500) + 500);

            // Scroll further down
            js.executeScript("window.scrollTo({top: " + scrollY2 + ", behavior: 'smooth'});");
            Thread.sleep(random.nextInt(1500) + 500);

            // Random mouse movement (if element exists)
            try {
                actions.moveByOffset(random.nextInt(200) + 50, random.nextInt(200) + 50).perform();
                Thread.sleep(random.nextInt(500) + 200);
            } catch (Exception e) {
                // Ignore if element not found
            }

            // Scroll back up slightly
            js.executeScript("window.scrollTo({top: " + scrollY3 + ", behavior: 'smooth'});");
            Thread.sleep(random.nextInt(1000) + 300);

            // Random zoom level simulation (optional)
            if (random.nextBoolean()) {
                actions.keyDown(Keys.CONTROL).sendKeys(Keys.ADD).keyUp(Keys.CONTROL).perform();
                Thread.sleep(200);
                actions.keyDown(Keys.CONTROL).sendKeys(Keys.SUBTRACT).keyUp(Keys.CONTROL).perform();
            }

            log.debug("Human behavior simulation completed");
        } catch (Exception e) {
            log.debug("Human behavior simulation failed: {}", e.getMessage());
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
            js.executeScript("Object.defineProperty(navigator, 'languages', {get: () => ['en-US', 'en', 'fr', 'ar']})");

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

            // Override navigator properties
            js.executeScript(
                    "Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});" +
                            "Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => navigator.hardwareConcurrency || 8});" +
                            "Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});" +
                            "Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 0});" +
                            "Object.defineProperty(navigator, 'vendor', {get: () => 'Google Inc.'});" +
                            "Object.defineProperty(navigator, 'vendorSub', {get: () => ''});" +
                            "Object.defineProperty(navigator, 'productSub', {get: () => '20030107'});"
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

            // Override WebGL vendor
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