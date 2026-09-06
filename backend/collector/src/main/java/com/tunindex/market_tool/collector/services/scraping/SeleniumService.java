package com.tunindex.market_tool.collector.services.scraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class SeleniumService {

    public SeleniumService() {
        WebDriverManager.chromedriver().setup();
    }

    public String getPageSource(String url) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36");

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);
            return driver.getPageSource();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (org.openqa.selenium.WebDriverException e) {
                    log.warn("WebDriverException during quit: {}", e.getMessage());
                } catch (Exception e) {
                    log.error("General exception during quit: {}", e.getMessage());
                }
            }
        }
    }
}
