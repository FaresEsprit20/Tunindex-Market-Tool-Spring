package com.fares.stock.management.domain.services.impl.recaptcha;

import com.tunindex.market_tool.common.exception.RecaptchaException;
import com.tunindex.market_tool.recaptcha.dto.RecaptchaV3Response;
import com.tunindex.market_tool.recaptcha.service.RecaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate;
    private final Environment environment;

    public RecaptchaServiceImpl(
            @Value("${recaptcha.secret-key}") String secretKey,
            @Value("${recaptcha.site-key}") String siteKey,
            @Value("${recaptcha.score-threshold:0.5}") float scoreThreshold,
            @Value("${recaptcha.allowed-hostnames:localhost,127.0.0.1}") String[] allowedHostnames,
            @Value("${recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}") String verifyUrl,
            RestTemplate restTemplate,
            Environment environment
    ) {
        this.secretKey = secretKey;
        this.siteKey = siteKey;
        this.scoreThreshold = scoreThreshold;
        this.allowedHostnames = List.of(allowedHostnames);
        this.verifyUrl = verifyUrl;
        this.restTemplate = restTemplate;
        this.environment = environment;
    }

    @Override
    public boolean validate(String recaptchaToken, String userIp, String expectedAction) {
        // ❗ Skip validation for dev and test
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

        } catch (RestClientException e) {
            errors.add("REST error: " + e.getMessage());
            throw new RecaptchaException("Verification failed",
                    ErrorCodes.RECAPTCHA_VERIFICATION_FAILED, errors);
        } catch (Exception e) {
            errors.add("Unexpected error: " + e.getMessage());
            throw new RecaptchaException("Internal error",
                    ErrorCodes.RECAPTCHA_INTERNAL_ERROR, errors);
        }
    }

    private RecaptchaV3Response validateTokenWithGoogle(String recaptchaToken, String userIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("siteKey", siteKey);
        formData.add("secret", secretKey);
        formData.add("response", recaptchaToken);

        if (userIp != null && !userIp.trim().isEmpty()) {
            formData.add("remoteip", userIp);
        }

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(formData, headers);

        ResponseEntity<RecaptchaV3Response> response = restTemplate.exchange(
                verifyUrl,
                HttpMethod.POST,
                entity,
                RecaptchaV3Response.class
        );

        return response.getBody();
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
