package com.tunindex.market_tool.collector.services.scraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
public class SeleniumService {

    @Value("${market-tool.browser.version}")
    private String browserVersion;

    // Limit to 2 concurrent drivers to prevent memory exhaustion
    private final Semaphore semaphore = new Semaphore(2);

    @PostConstruct
    public void init() {
        WebDriverManager.chromedriver()
                .browserVersion(browserVersion)
                .setup();
    }

    public String getPageSource(String url) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for driver semaphore", e);
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + browserVersion + ".0.0.0 Safari/537.36");

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(60));
            driver.get(url);
            return driver.getPageSource();
        } catch (Exception e) {
            log.error("Error during Selenium scraping of {}: {}", url, e.getMessage());
            throw e; 
        } finally {
            if (driver != null) {
                // Perform cleanup in a separate thread so it cannot block the request thread
                WebDriver driverToQuit = driver;
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        driverToQuit.quit();
                    } catch (Exception e) {
                        log.warn("Graceful quit failed, attempting forceful shutdown: {}", e.getMessage());
                        // Fallback: If graceful quit fails, the process is likely hung.
                        // This is a last-resort approach for Windows.
                        try {
                            Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe /T");
                        } catch (Exception ex) {
                            log.error("Forceful shutdown failed: {}", ex.getMessage());
                        }
                    }
                });
            }
            semaphore.release();
        }
    }
}
