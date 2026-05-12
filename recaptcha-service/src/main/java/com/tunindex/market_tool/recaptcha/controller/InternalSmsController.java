package com.tunindex.market_tool.recaptcha.controller;

import com.tunindex.market_tool.common.exception.InvalidPhoneNumberException;
import com.tunindex.market_tool.common.exception.SmsServiceException;
import com.tunindex.market_tool.recaptcha.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/sms")
@RequiredArgsConstructor
@Slf4j
public class InternalSmsController {

    private final SmsService smsService;
    private final SmsNewsletterService smsNewsletterService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    /**
     * Send single SMS to a phone number
     * POST /internal/sms/send
     * Body: { "to": "+21612345678", "message": "Hello" }
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendSms(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!internalApiKey.equals(apiKey)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid API key"));
        }

        try {
            String to = request.get("to");
            String message = request.get("message");

            if (to == null || message == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing 'to' or 'message' field"));
            }

            smsService.sendSms(to, message);
            log.info("SMS sent to: {}", to);
            return ResponseEntity.ok().body(Map.of("success", true));

        } catch (InvalidPhoneNumberException e) {
            log.error("Invalid phone number: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (SmsServiceException e) {
            log.error("SMS service error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Send SMS to ALL users (newsletter)
     * POST /internal/sms/newsletter/all
     */
    @PostMapping("/newsletter/all")
    public ResponseEntity<?> sendNewsletterToAll(
            @RequestBody SendToAllSmsDto dto,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!internalApiKey.equals(apiKey)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid API key"));
        }

        try {
            smsNewsletterService.sendSmsToAllUsers(dto.getMessage());
            log.info("Newsletter SMS sent to ALL users");
            return ResponseEntity.ok().body(Map.of("success", true));

        } catch (InvalidPhoneNumberException e) {
            log.error("Invalid phone number: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (SmsServiceException e) {
            log.error("SMS service error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Send SMS to users by role
     * POST /internal/sms/newsletter/role
     */
    @PostMapping("/newsletter/role")
    public ResponseEntity<?> sendNewsletterToRole(
            @RequestBody SendToRoleSmsDto dto,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!internalApiKey.equals(apiKey)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid API key"));
        }

        try {
            smsNewsletterService.sendSmsToUsersByRole(dto.getRole(), dto.getMessage());
            log.info("Newsletter SMS sent to users with role: {}", dto.getRole());
            return ResponseEntity.ok().body(Map.of("success", true));

        } catch (InvalidPhoneNumberException e) {
            log.error("Invalid phone number: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (SmsServiceException e) {
            log.error("SMS service error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Send SMS to a specific user by phone number
     * POST /internal/sms/newsletter/user
     */
    @PostMapping("/newsletter/user")
    public ResponseEntity<?> sendNewsletterToUser(
            @RequestBody SendToUserSmsDto dto,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!internalApiKey.equals(apiKey)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid API key"));
        }

        try {
            smsNewsletterService.sendSmsToUser(dto.getPhoneNumber(), dto.getMessage());
            log.info("SMS sent to user: {}", dto.getPhoneNumber());
            return ResponseEntity.ok().body(Map.of("success", true));

        } catch (InvalidPhoneNumberException e) {
            log.error("Invalid phone number: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (SmsServiceException e) {
            log.error("SMS service error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Send SMS to a specific user by email
     * POST /internal/sms/newsletter/user-by-email
     */
    @PostMapping("/newsletter/user-by-email")
    public ResponseEntity<?> sendNewsletterToUserByEmail(
            @RequestBody Map<String, String> request,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!internalApiKey.equals(apiKey)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid API key"));
        }

        try {
            String email = request.get("email");
            String message = request.get("message");

            if (email == null || message == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing 'email' or 'message' field"));
            }

            smsNewsletterService.sendSmsToUserByEmail(email, message);
            log.info("SMS sent to user by email: {}", email);
            return ResponseEntity.ok().body(Map.of("success", true));

        } catch (InvalidPhoneNumberException e) {
            log.error("Invalid phone number: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (SmsServiceException e) {
            log.error("SMS service error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Send SMS to a specific user by user ID
     * POST /internal/sms/newsletter/user-by-id
     */
    @PostMapping("/newsletter/user-by-id")
    public ResponseEntity<?> sendNewsletterToUserById(
            @RequestBody Map<String, Object> request,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!internalApiKey.equals(apiKey)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid API key"));
        }

        try {
            Long userId = ((Number) request.get("userId")).longValue();
            String message = (String) request.get("message");

            if (userId == null || message == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Missing 'userId' or 'message' field"));
            }

            smsNewsletterService.sendSmsToUserById(userId, message);
            log.info("SMS sent to user by ID: {}", userId);
            return ResponseEntity.ok().body(Map.of("success", true));

        } catch (InvalidPhoneNumberException e) {
            log.error("Invalid phone number: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (SmsServiceException e) {
            log.error("SMS service error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}