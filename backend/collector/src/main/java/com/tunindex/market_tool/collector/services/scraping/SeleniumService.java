package com.tunindex.market_tool.collector.services.scraping;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A real browser, for the pages that genuinely need one.
 *
 * <p>This is the fallback path behind {@link PageFetcher}, not the default.
 * Launching Chrome costs a few hundred megabytes and several seconds per page
 * against roughly 0.4s and no memory for an ordinary HTTP GET, so it is only
 * worth paying when a site actually refuses the cheap route.
 *
 * <p>The driver lifecycle here is deliberate, because the previous version
 * leaked processes in three separate ways:
 *
 * <ul>
 *   <li>The permit was released before {@code quit()} finished, so the
 *       semaphore bounded <em>launches</em> rather than <em>live browsers</em>
 *       — under a sequential sweep of every symbol, browsers accumulated
 *       without limit.
 *   <li>{@code quit()} ran on a detached {@code CompletableFuture} that
 *       nothing ever waited on or logged, so a hung shutdown was invisible.
 *   <li>The last-resort {@code taskkill} targeted {@code chromedriver.exe}
 *       only. Chrome is a child process tree of its own, so killing the driver
 *       orphaned every {@code chrome.exe} it had spawned.
 * </ul>
 *
 * <p>Now: one permit is held for the whole life of a driver, shutdown is
 * synchronous with a bounded wait, and the fallback kills the browser too.
 */
@Service
@Slf4j
public class SeleniumService {

    @Value("${market-tool.browser.version:}")
    private String browserVersion;

    /**
     * Concurrent browsers. One by default: the callers are sequential, and
     * every extra permit is another few hundred megabytes of Chrome.
     */
    @Value("${market-tool.browser.max-concurrent:1}")
    private int maxConcurrent;

    @Value("${market-tool.browser.page-load-timeout-seconds:45}")
    private long pageLoadTimeoutSeconds;

    /** How long to wait for a driver to shut down before killing it. */
    @Value("${market-tool.browser.quit-timeout-seconds:20}")
    private long quitTimeoutSeconds;

    private Semaphore semaphore;

    /**
     * Whether the driver could be provisioned at all. When Chrome is missing,
     * every call fails fast with a clear message instead of throwing a driver
     * exception per page for the rest of the run.
     */
    private volatile boolean available;

    @PostConstruct
    void init() {
        semaphore = new Semaphore(Math.max(1, maxConcurrent));
        try {
            WebDriverManager manager = WebDriverManager.chromedriver();
            if (browserVersion != null && !browserVersion.isBlank()) {
                manager = manager.browserVersion(browserVersion);
            }
            manager.setup();
            available = true;
            log.info("Selenium fallback ready (max {} concurrent browser(s))", maxConcurrent);
        } catch (Exception e) {
            // Not fatal. The HTTP path is the one that matters; losing the
            // fallback should not stop the service from starting.
            available = false;
            log.warn("Selenium fallback unavailable — browser escalation is disabled: {}", e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * The rendered page source, or null when it could not be fetched.
     *
     * <p>Returns null rather than throwing so a browser failure is handled the
     * same way as any other missed page, by the caller carrying on.
     */
    public String getPageSource(String url) {
        if (!available) {
            log.debug("Skipping browser fetch of {} — no usable driver", url);
            return null;
        }

        try {
            // Bounded: if every permit is held by a browser that will not die,
            // the caller gets a miss rather than blocking the pass forever.
            if (!semaphore.tryAcquire(2, TimeUnit.MINUTES)) {
                log.warn("Timed out waiting for a browser slot for {}", url);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(buildOptions());
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(pageLoadTimeoutSeconds));
            driver.get(url);
            return driver.getPageSource();
        } catch (Exception e) {
            log.warn("Browser fetch of {} failed: {}", url, e.getMessage());
            return null;
        } finally {
            // Synchronous, and only then is the permit released — that is what
            // makes the semaphore bound live browsers rather than launches.
            quitQuietly(driver);
            semaphore.release();
        }
    }

    private ChromeOptions buildOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        // Trims a large amount of startup work we have no use for, which is
        // most of the launch cost.
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-background-networking");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments("--mute-audio");
        options.addArguments("--blink-settings=imagesEnabled=false");

        // Without these, Chrome announces itself: an "automation" infobar and
        // navigator.webdriver = true. Since the point of using a browser here
        // is to look like one, leaving them on defeats the exercise.
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        return options;
    }

    /**
     * Shuts a driver down and makes sure the browser it started is gone.
     *
     * <p>{@code quit()} is run on a daemon thread purely so a hung driver can
     * be timed out — the calling thread still waits for the result, and only
     * gives up after {@code quitTimeoutSeconds}.
     */
    private void quitQuietly(WebDriver driver) {
        if (driver == null) {
            return;
        }
        Thread quitter = new Thread(() -> {
            try {
                driver.quit();
            } catch (Exception e) {
                log.debug("Driver quit raised: {}", e.getMessage());
            }
        }, "selenium-quit");
        quitter.setDaemon(true);
        quitter.start();

        try {
            quitter.join(Duration.ofSeconds(quitTimeoutSeconds).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (quitter.isAlive()) {
            log.warn("Driver did not shut down within {}s — force-killing the browser", quitTimeoutSeconds);
            forceKill();
        }
    }

    /**
     * Last resort, and only reached when a driver has already hung.
     *
     * <p>Kills the driver <em>and</em> the browser: chromedriver spawns Chrome
     * as a separate process tree, so killing the driver alone leaves the
     * browser behind — which is how orphaned chrome.exe processes accumulate.
     *
     * <p>Windows-specific, and deliberately narrow: it only runs on the
     * timeout path, so it cannot interfere with a browser the user has open
     * under normal operation. This would need an equivalent on Linux.
     */
    private void forceKill() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            log.warn("No force-kill implemented for this OS; a browser process may be left behind");
            return;
        }
        for (String image : List.of("chromedriver.exe", "chrome.exe")) {
            try {
                Process process = new ProcessBuilder("taskkill", "/F", "/T", "/IM", image)
                        .redirectErrorStream(true)
                        .start();
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Force-kill of {} failed: {}", image, e.getMessage());
            }
        }
    }

    @PreDestroy
    void shutdown() {
        // Nothing to tear down when every driver is quit inside its own call;
        // this exists so a leaked permit is visible at shutdown rather than
        // silently ignored.
        int held = Math.max(0, Math.max(1, maxConcurrent) - semaphore.availablePermits());
        if (held > 0) {
            log.warn("Shutting down with {} browser permit(s) still held", held);
        }
    }
}
