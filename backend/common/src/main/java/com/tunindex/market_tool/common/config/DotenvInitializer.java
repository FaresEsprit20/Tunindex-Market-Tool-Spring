package com.tunindex.market_tool.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Makes {@code backend/.env} available as Spring properties.
 *
 * <p>Shared by every service rather than copied into each, which is the point:
 * the version this replaces lived in the api service and printed
 * {@code "Loaded .env property: " + key + "=" + value} for every entry. That
 * wrote the Google client secret and the IP salt into stdout on each boot -
 * a hundred such lines in one log file here - so the secrets were out of the
 * repository and into the logs instead, which is not an improvement.
 *
 * <p>This logs the key <em>names</em> only. Knowing which variables were found
 * is what makes a misconfiguration diagnosable; knowing their values only ever
 * helps an attacker.
 */
public class DotenvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger log = LoggerFactory.getLogger(DotenvInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();

        try {
            Dotenv dotenv = Dotenv.configure()
                    // Services are started from backend/, so the file sits in
                    // the working directory; the parent is tried as well so a
                    // run from a module directory still finds it.
                    .directory("./")
                    .ignoreIfMissing()
                    .load();

            Map<String, Object> properties = new HashMap<>();
            dotenv.entries().forEach(entry -> properties.put(entry.getKey(), entry.getValue()));

            if (properties.isEmpty()) {
                log.info("No .env entries found; relying on system environment variables");
                return;
            }

            // addFirst: .env wins over application.properties, so a checked-in
            // default can be overridden without editing the file.
            environment.getPropertySources().addFirst(new MapPropertySource("dotenv", properties));

            // Names only. Never values.
            log.info("Loaded {} .env entries: {}", properties.size(),
                    String.join(", ", properties.keySet()));

        } catch (Exception e) {
            log.warn("Could not read .env ({}); using system environment variables", e.getMessage());
        }
    }
}
