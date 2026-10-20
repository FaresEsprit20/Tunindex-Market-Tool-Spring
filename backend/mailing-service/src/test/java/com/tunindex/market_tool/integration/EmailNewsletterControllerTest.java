package com.tunindex.market_tool.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.mailing.controller.EmailNewsletterController;
import com.tunindex.market_tool.mailing.dto.SendToAllNewsletterDto;
import com.tunindex.market_tool.mailing.dto.SendToRoleNewsletterDto;
import com.tunindex.market_tool.mailing.dto.SendToUserNewsletterDto;
import com.tunindex.market_tool.mailing.dto.UserRole;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmailNewsletterControllerTest {

    private MockMvc mockMvc;

    @Mock
    private NewsletterService newsletterService;

    @InjectMocks
    private EmailNewsletterController emailNewsletterController;

    private ObjectMapper objectMapper;
    private final String validApiKey = "market-tool-internal-secret-key-2026";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(emailNewsletterController).build();
        objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(emailNewsletterController, "internalApiKey", validApiKey);
    }

    @Test
    void sendToAll_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        SendToAllNewsletterDto dto = new SendToAllNewsletterDto();
        dto.setSubject("Newsletter Subject");
        dto.setContent("<h1>Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then
        mockMvc.perform(post("/api/newsletter/send-to-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(newsletterService, times(1)).sendNewsletterToAll(dto.getSubject(), dto.getContent(), dto.getLabel());
    }

    @Test
    void sendToAll_WithInvalidApiKey_ShouldThrowException() throws Exception {
        // Given
        SendToAllNewsletterDto dto = new SendToAllNewsletterDto();
        dto.setSubject("Newsletter Subject");
        dto.setContent("<h1>Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then
        mockMvc.perform(post("/api/newsletter/send-to-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", "invalid-key")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isInternalServerError());

        verify(newsletterService, never()).sendNewsletterToAll(anyString(), anyString(), anyString());
    }

    @Test
    void sendToAll_WithoutApiKey_ShouldThrowException() throws Exception {
        // Given
        SendToAllNewsletterDto dto = new SendToAllNewsletterDto();
        dto.setSubject("Newsletter Subject");
        dto.setContent("<h1>Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then
        mockMvc.perform(post("/api/newsletter/send-to-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isInternalServerError());

        verify(newsletterService, never()).sendNewsletterToAll(anyString(), anyString(), anyString());
    }

    @Test
    void sendToRole_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        SendToRoleNewsletterDto dto = new SendToRoleNewsletterDto();
        dto.setRole(UserRole.ADMIN);
        dto.setSubject("Admin Newsletter");
        dto.setContent("<h1>Admin Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then
        mockMvc.perform(post("/api/newsletter/send-to-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(newsletterService, times(1)).sendNewsletterToRole(dto.getRole(), dto.getSubject(), dto.getContent(), dto.getLabel());
    }

    @Test
    void sendToUser_WithValidApiKey_ShouldSucceed() throws Exception {
        // Given
        SendToUserNewsletterDto dto = new SendToUserNewsletterDto();
        dto.setEmail("user@example.com");
        dto.setSubject("User Newsletter");
        dto.setContent("<h1>User Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then
        mockMvc.perform(post("/api/newsletter/send-to-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        verify(newsletterService, times(1)).sendNewsletterToUser(dto.getEmail(), dto.getSubject(), dto.getContent(), dto.getLabel());
    }

    @Test
    void sendToUser_WithInvalidEmail_ShouldReturnBadRequest() throws Exception {
        // Given
        SendToUserNewsletterDto dto = new SendToUserNewsletterDto();
        dto.setEmail("invalid-email");
        dto.setSubject("User Newsletter");
        dto.setContent("<h1>Content</h1>");
        dto.setLabel("Market Tool");

        // When & Then - validation should fail
        mockMvc.perform(post("/api/newsletter/send-to-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-API-Key", validApiKey)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(newsletterService, never()).sendNewsletterToUser(anyString(), anyString(), anyString(), anyString());
    }


}