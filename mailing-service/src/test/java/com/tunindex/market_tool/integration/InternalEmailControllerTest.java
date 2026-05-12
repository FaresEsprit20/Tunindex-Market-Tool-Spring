package com.tunindex.market_tool.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.mailing.controller.InternalEmailController;
import com.tunindex.market_tool.mailing.dto.SendToAllNewsletterDto;
import com.tunindex.market_tool.mailing.dto.SendToRoleNewsletterDto;
import com.tunindex.market_tool.mailing.dto.SendToUserNewsletterDto;
import com.tunindex.market_tool.mailing.dto.UserRole;
import com.tunindex.market_tool.mailing.service.EmailService;
import com.tunindex.market_tool.mailing.service.NewsletterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InternalEmailControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EmailService emailService;

    @Mock
    private NewsletterService newsletterService;

    @InjectMocks
    private InternalEmailController internalEmailController;

    private ObjectMapper objectMapper;
    private final String validApiKey = "market-tool-internal-secret-key-2026";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(internalEmailController).build();
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(internalEmailController, "internalApiKey", validApiKey);
    }

    @Test
    void send2FA_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        Map<String, String> request = Map.of("email", "test@example.com", "otp", "123456");

        // When & Then
        mockMvc.perform(post("/internal/email/send-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailService, times(1)).sendTwoFactorAuthEmail("test@example.com", "123456");
    }

    @Test
    void send2FA_WithInvalidApiKey_ShouldReturnUnauthorized() throws Exception {
        // Given
        Map<String, String> request = Map.of("email", "test@example.com", "otp", "123456");

        // When & Then
        mockMvc.perform(post("/internal/email/send-2fa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "invalid-key")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(emailService, never()).sendTwoFactorAuthEmail(anyString(), anyString());
    }

    @Test
    void sendHtml_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        Map<String, String> request = Map.of(
                "to", "test@example.com",
                "subject", "Test Subject",
                "content", "<h1>Test</h1>",
                "label", "Test Label"
        );

        // When & Then
        mockMvc.perform(post("/internal/email/send-html")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailService, times(1)).sendHtmlMessage("test@example.com", "Test Subject", "<h1>Test</h1>", "Test Label");
    }

    @Test
    void sendSimple_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        Map<String, String> request = Map.of(
                "to", "test@example.com",
                "subject", "Simple Subject",
                "content", "Simple content",
                "label", "Simple Label"
        );

        // When & Then
        mockMvc.perform(post("/internal/email/send-simple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(emailService, times(1)).sendSimpleMessage("test@example.com", "Simple Subject", "Simple content", "Simple Label");
    }

    @Test
    void sendNewsletterToAll_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        SendToAllNewsletterDto dto = new SendToAllNewsletterDto();
        dto.setSubject("Newsletter Subject");
        dto.setContent("<h1>Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then
        mockMvc.perform(post("/internal/email/newsletter/all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(newsletterService, times(1)).sendNewsletterToAll(dto.getSubject(), dto.getContent(), dto.getLabel());
    }

    @Test
    void sendNewsletterToRole_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        SendToRoleNewsletterDto dto = new SendToRoleNewsletterDto();
        dto.setRole(UserRole.ADMIN);
        dto.setSubject("Admin Newsletter");
        dto.setContent("<h1>Admin Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then
        mockMvc.perform(post("/internal/email/newsletter/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(newsletterService, times(1)).sendNewsletterToRole(dto.getRole(), dto.getSubject(), dto.getContent(), dto.getLabel());
    }

    @Test
    void sendNewsletterToUser_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        SendToUserNewsletterDto dto = new SendToUserNewsletterDto();
        dto.setEmail("user@example.com");
        dto.setSubject("User Newsletter");
        dto.setContent("<h1>User Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then
        mockMvc.perform(post("/internal/email/newsletter/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(newsletterService, times(1)).sendNewsletterToUser(dto.getEmail(), dto.getSubject(), dto.getContent(), dto.getLabel());
    }
}