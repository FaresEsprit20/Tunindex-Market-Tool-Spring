package com.tunindex.market_tool.ads;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

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
public class AdModuleApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdModuleApplication.class, args);
    }
}
