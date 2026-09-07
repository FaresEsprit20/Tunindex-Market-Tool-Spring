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
            throw e; // Rethrow to let the provider handle it via onErrorResume
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("WebDriverException during quit: {}", e.getMessage());
                    // On Windows, if quit() fails, the process often remains. 
                    // This is a simple attempt to log it; deeper cleanup would require OS-specific commands.
                }
            }
            semaphore.release();
        }
    }
}
