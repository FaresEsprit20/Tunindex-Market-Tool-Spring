package com.tunindex.market_tool.sms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/newsletter")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Newsletter", description = "API for sending newsletter emails")
public class EmailNewsletterController {

    private final NewsletterService newsletterService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @PostMapping("/send-to-all")
    @Operation(summary = "Send newsletter to all users")
    public ResponseEntity<Void> sendToAll(
            @Valid @RequestBody SendToAllNewsletterDto dto,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        log.info("POST /api/newsletter/send-to-all - Subject: {}", dto.getSubject());
        newsletterService.sendNewsletterToAll(dto.getSubject(), dto.getContent(), dto.getLabel());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send-to-role")
    @Operation(summary = "Send newsletter to users by role")
    public ResponseEntity<Void> sendToRole(
            @Valid @RequestBody SendToRoleNewsletterDto dto,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        log.info("POST /api/newsletter/send-to-role - Role: {}, Subject: {}", dto.getRole(), dto.getSubject());
        newsletterService.sendNewsletterToRole(dto.getRole(), dto.getSubject(), dto.getContent(), dto.getLabel());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/send-to-user")
    @Operation(summary = "Send newsletter to a specific user")
    public ResponseEntity<Void> sendToUser(
            @Valid @RequestBody SendToUserNewsletterDto dto,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        log.info("POST /api/newsletter/send-to-user - Email: {}, Subject: {}", dto.getEmail(), dto.getSubject());
        newsletterService.sendNewsletterToUser(dto.getEmail(), dto.getSubject(), dto.getContent(), dto.getLabel());
        return ResponseEntity.ok().build();
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("Invalid or missing API key for internal call");
            throw new SecurityException("Invalid or missing API key");
        }
    }
}