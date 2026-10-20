package com.tunindex.market_tool.ads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ad inventory, delivery decisions and revenue accounting.
 *
 * <p>A separate service because its write pattern is unlike the rest of the
 * platform: an event row per impression is high-volume and append-only, and
 * mixing that into the API's database would put ad traffic in the way of a
 * user checking their portfolio.
 */
@SpringBootApplication
@EnableDiscoveryClient
// Abandoned ad-gate view sessions are swept on a timer; without this the
// sweep never runs and the table grows for every ad someone closed early.
@EnableScheduling
public class AdModuleApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdModuleApplication.class, args);
    }
}
