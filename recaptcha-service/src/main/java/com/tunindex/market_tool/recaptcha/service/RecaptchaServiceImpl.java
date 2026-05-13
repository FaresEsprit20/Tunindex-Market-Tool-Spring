package com.tunindex.market_tool.recaptcha.service;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.RecaptchaException;
import com.tunindex.market_tool.recaptcha.dto.RecaptchaV3Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class RecaptchaServiceImpl implements RecaptchaService {

    private final String secretKey;
    private final String siteKey;
    private final float scoreThreshold;
    private final List<String> allowedHostnames;
    private final String verifyUrl;
    private final WebClient webClient;
    private final Environment environment;

    public RecaptchaServiceImpl(
            @Value("${recaptcha.secret-key}") String secretKey,
            @Value("${recaptcha.site-key}") String siteKey,
            @Value("${recaptcha.score-threshold:0.5}") float scoreThreshold,
            @Value("${recaptcha.allowed-hostnames:localhost,127.0.0.1}") String[] allowedHostnames,
            @Value("${recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}") String verifyUrl,
            WebClient.Builder webClientBuilder,
            Environment environment
    ) {
        this.secretKey = secretKey;
        this.siteKey = siteKey;
        this.scoreThreshold = scoreThreshold;
        this.allowedHostnames = List.of(allowedHostnames);
        this.verifyUrl = verifyUrl;
        this.webClient = webClientBuilder.build();
        this.environment = environment;
    }

    @Override
    public boolean validate(String recaptchaToken, String userIp, String expectedAction) {
        // Skip validation for dev and test
        if (isDevOrTestProfile()) {
            log.warn("Bypassing reCAPTCHA validation for dev/test profile.");
            return true;
        }

        List<String> errors = new ArrayList<>();

        try {
            if (recaptchaToken == null || recaptchaToken.trim().isEmpty()) {
                errors.add("reCAPTCHA token is null or empty");
                throw new RecaptchaException("Token missing",
                        ErrorCodes.RECAPTCHA_TOKEN_ABSENT, errors);
            }

            RecaptchaV3Response response = validateTokenWithGoogle(recaptchaToken, userIp);

            if (response == null) {
                errors.add("Empty response from reCAPTCHA server");
                throw new RecaptchaException("Empty response",
                        ErrorCodes.RECAPTCHA_RESPONSE_NULL, errors);
            }

            if (!response.isSuccess()) {
                errors.add("Verification failed");
                throw new RecaptchaException("Verification failed",
                        ErrorCodes.RECAPTCHA_TOKEN_FAILED, errors);
            }

            if (response.getScore() != null && response.getScore() < scoreThreshold) {
                errors.add("Score too low");
                throw new RecaptchaException("Score too low",
                        ErrorCodes.RECAPTCHA_SCORE_LOW, errors);
            }

            if (expectedAction != null && !expectedAction.equalsIgnoreCase(response.getAction())) {
                errors.add("Action mismatch");
                throw new RecaptchaException("Action mismatch",
                        ErrorCodes.RECAPTCHA_ACTION_MISMATCH, errors);
            }

            if (response.getHostname() != null && !isValidHostname(response.getHostname())) {
                errors.add("Invalid hostname: " + response.getHostname());
                throw new RecaptchaException("Invalid hostname",
                        ErrorCodes.RECAPTCHA_HOSTNAME_INVALID, errors);
            }

            return true;

        } catch (Exception e) {
            if (e instanceof RecaptchaException) {
                throw e;
            }
            errors.add("Unexpected error: " + e.getMessage());
            throw new RecaptchaException("Internal error",
                    ErrorCodes.RECAPTCHA_INTERNAL_ERROR, errors);
        }
    }

    private RecaptchaV3Response validateTokenWithGoogle(String recaptchaToken, String userIp) {
        try {
            return webClient.post()
                    .uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(buildFormData(recaptchaToken, userIp))
                    .retrieve()
                    .bodyToMono(RecaptchaV3Response.class)
                    .block();

        } catch (Exception e) {
            log.error("Error calling Google reCAPTCHA API: {}", e.getMessage());
            throw new RuntimeException("Failed to validate reCAPTCHA token", e);
        }
    }

    private String buildFormData(String recaptchaToken, String userIp) {
        StringBuilder formData = new StringBuilder();
        formData.append("secret=").append(secretKey);
        formData.append("&response=").append(recaptchaToken);
        if (userIp != null && !userIp.trim().isEmpty()) {
            formData.append("&remoteip=").append(userIp);
        }
        return formData.toString();
    }

    private boolean isValidHostname(String hostname) {
        return allowedHostnames.contains(hostname);
    }

    private boolean isDevOrTestProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("dev") ||
                        profile.equalsIgnoreCase("test"));
    }


}