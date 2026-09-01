package com.tunindex.market_tool.recaptcha.controller;

import com.tunindex.market_tool.recaptcha.service.RecaptchaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/recaptcha")
@RequiredArgsConstructor
@Slf4j
public class InternalRecaptchaController {

    private final RecaptchaService recaptchaService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("Invalid internal API key for recaptcha service");
            throw new SecurityException("Invalid API key");
        }
    }

    /**
     * Verify recaptcha token
     * POST /internal/recaptcha/validate
     * Body: { "token": "recaptcha-token", "userIp": "192.168.1.1", "action": "login" }
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-API-Key") String apiKey) {

        validateApiKey(apiKey);

        String token = request.get("token");
        String userIp = request.get("userIp");
        String action = request.get("action");

        log.info("Validating recaptcha token for action: {}, userIp: {}", action, userIp);

        boolean isValid = recaptchaService.validate(token, userIp, action);

        return ResponseEntity.ok().body(Map.of(
                "success", isValid,
                "message", isValid ? "Recaptcha validation successful" : "Recaptcha validation failed"
        ));
    }


}