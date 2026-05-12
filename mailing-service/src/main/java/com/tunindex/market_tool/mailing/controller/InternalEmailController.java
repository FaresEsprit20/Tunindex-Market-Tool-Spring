package com.tunindex.market_tool.mailing.controller;

import com.tunindex.market_tool.mailing.dto.SendToAllNewsletterDto;
import com.tunindex.market_tool.mailing.dto.SendToRoleNewsletterDto;
import com.tunindex.market_tool.mailing.dto.SendToUserNewsletterDto;
import com.tunindex.market_tool.mailing.service.EmailService;
import com.tunindex.market_tool.mailing.service.NewsletterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/internal/email")
@RequiredArgsConstructor
@Slf4j
public class InternalEmailController {

    private final EmailService emailService;
    private final NewsletterService newsletterService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @PostMapping("/send-2fa")
    public ResponseEntity<?> send2FA(@RequestBody Map<String, String> request,
                                     @RequestHeader("X-API-Key") String apiKey) {
        if (!internalApiKey.equals(apiKey)) return ResponseEntity.status(401).body("Invalid API key");
        try {
            emailService.sendTwoFactorAuthEmail(request.get("email"), request.get("otp"));
            return ResponseEntity.ok().body(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/send-html")
    public ResponseEntity<?> sendHtml(@RequestBody Map<String, String> request,
                                      @RequestHeader("X-API-Key") String apiKey) {
        if (!internalApiKey.equals(apiKey)) return ResponseEntity.status(401).body("Invalid API key");
        try {
            emailService.sendHtmlMessage(request.get("to"), request.get("subject"), request.get("content"), request.get("label"));
            return ResponseEntity.ok().body(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/send-simple")
    public ResponseEntity<?> sendSimple(@RequestBody Map<String, String> request,
                                        @RequestHeader("X-API-Key") String apiKey) {
        if (!internalApiKey.equals(apiKey)) return ResponseEntity.status(401).body("Invalid API key");
        try {
            emailService.sendSimpleMessage(request.get("to"), request.get("subject"), request.get("content"), request.get("label"));
            return ResponseEntity.ok().body(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // Newsletter endpoints for internal use
    @PostMapping("/newsletter/all")
    public ResponseEntity<?> sendNewsletterToAll(@RequestBody SendToAllNewsletterDto dto,
                                                 @RequestHeader("X-API-Key") String apiKey) {
        if (!internalApiKey.equals(apiKey)) return ResponseEntity.status(401).body("Invalid API key");
        newsletterService.sendNewsletterToAll(dto.getSubject(), dto.getContent(), dto.getLabel());
        return ResponseEntity.ok().body(Map.of("success", true));
    }

    @PostMapping("/newsletter/role")
    public ResponseEntity<?> sendNewsletterToRole(@RequestBody SendToRoleNewsletterDto dto,
                                                  @RequestHeader("X-API-Key") String apiKey) {
        if (!internalApiKey.equals(apiKey)) return ResponseEntity.status(401).body("Invalid API key");
        newsletterService.sendNewsletterToRole(dto.getRole(), dto.getSubject(), dto.getContent(), dto.getLabel());
        return ResponseEntity.ok().body(Map.of("success", true));
    }

    @PostMapping("/newsletter/user")
    public ResponseEntity<?> sendNewsletterToUser(@RequestBody SendToUserNewsletterDto dto,
                                                  @RequestHeader("X-API-Key") String apiKey) {
        if (!internalApiKey.equals(apiKey)) return ResponseEntity.status(401).body("Invalid API key");
        newsletterService.sendNewsletterToUser(dto.getEmail(), dto.getSubject(), dto.getContent(), dto.getLabel());
        return ResponseEntity.ok().body(Map.of("success", true));
    }
}