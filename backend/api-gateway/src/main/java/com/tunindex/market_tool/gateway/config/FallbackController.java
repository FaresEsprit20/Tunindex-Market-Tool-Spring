package com.tunindex.market_tool.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What callers get when a service behind the gateway is unreachable.
 *
 * <p>A circuit breaker without a fallback surfaces as a raw connection error,
 * which the client cannot distinguish from its own bug. Answering with a
 * shaped 503 lets the UI say "this part is unavailable" and keep the rest of
 * the page working.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/{service}")
    public ResponseEntity<Map<String, Object>> get(@org.springframework.web.bind.annotation.PathVariable String service) {
        return unavailable(service);
    }

    @PostMapping("/{service}")
    public ResponseEntity<Map<String, Object>> post(@org.springframework.web.bind.annotation.PathVariable String service) {
        return unavailable(service);
    }

    private ResponseEntity<Map<String, Object>> unavailable(String service) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("httpCode", HttpStatus.SERVICE_UNAVAILABLE.value());
        // Same shape the services' own error handler produces, so a client
        // parses one format rather than special-casing gateway failures.
        body.put("code", "SERVICE_UNAVAILABLE");
        body.put("message", service + " is not responding right now");
        body.put("errors", java.util.List.of("The request did not reach " + service + ". Please try again shortly."));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
