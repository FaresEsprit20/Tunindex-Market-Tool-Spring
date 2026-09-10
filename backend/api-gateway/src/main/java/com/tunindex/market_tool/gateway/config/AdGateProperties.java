package com.tunindex.market_tool.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which paths cost an ad.
 *
 * <p>Bound as configuration properties rather than with {@code @Value},
 * because a map cannot be injected that way - Spring has no converter from
 * the single string a placeholder resolves to, and the context fails to start
 * with a conversion error rather than a hint about the real cause.
 *
 * <p>Order is preserved so the rules are matched in the order they are
 * written. With overlapping patterns that is the difference between a
 * specific rule taking effect and a broader one further up swallowing it.
 */
@Component
@ConfigurationProperties(prefix = "gateway.ad-gate")
@Getter
@Setter
public class AdGateProperties {

    /**
     * Off switch for the whole gate.
     *
     * <p>Worth having separately from an empty rule list: it turns every gate
     * off in one edit if the ad service is in trouble, without losing the
     * rules themselves.
     */
    private boolean enabled = true;

    /** Ant path pattern to the feature whose grant opens it. */
    private Map<String, String> rules = new LinkedHashMap<>();
}
