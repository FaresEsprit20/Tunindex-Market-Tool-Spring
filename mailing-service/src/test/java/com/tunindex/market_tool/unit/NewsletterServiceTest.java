package com.tunindex.market_tool.unit;

import com.tunindex.market_tool.mailing.dto.UserEmailDto;
import com.tunindex.market_tool.mailing.dto.UserRole;
import com.tunindex.market_tool.mailing.service.ApiServiceClient;
import com.tunindex.market_tool.mailing.service.EmailService;
import com.tunindex.market_tool.mailing.service.NewsletterServiceImpl;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsletterServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ApiServiceClient apiServiceClient;

    @InjectMocks
    private NewsletterServiceImpl newsletterService;

    private final String subject = "Newsletter Subject";
    private final String content = "<h1>Newsletter Content</h1>";
    private final String label = "Market Tool Newsletter";

    private List<UserEmailDto> mockUsers;

    @BeforeEach
    void setUp() {
        mockUsers = Arrays.asList(
                UserEmailDto.builder().id(1L).email("user1@example.com").firstName("John").lastName("Doe").build(),
                UserEmailDto.builder().id(2L).email("user2@example.com").firstName("Jane").lastName("Smith").build(),
                UserEmailDto.builder().id(3L).email("user3@example.com").firstName("Bob").lastName("Johnson").build()
        );
    }

    @Test
    void sendNewsletterToAll_Success() throws Exception {
        // Given
        when(apiServiceClient.getAllUserEmails()).thenReturn(mockUsers);

        // When
        newsletterService.sendNewsletterToAll(subject, content, label);

        // Then
        verify(apiServiceClient, times(1)).getAllUserEmails();
        verify(emailService, times(mockUsers.size())).sendHtmlMessage(anyString(), eq(subject), eq(content), eq(label));

        for (UserEmailDto user : mockUsers) {
            verify(emailService, times(1)).sendHtmlMessage(user.getEmail(), subject, content, label);
        }
    }

    @Test
    void sendNewsletterToAll_WhenNoUsers_ShouldNotSendEmails() throws MessagingException, UnsupportedEncodingException {
        // Given
        when(apiServiceClient.getAllUserEmails()).thenReturn(Collections.emptyList());

        // When
        newsletterService.sendNewsletterToAll(subject, content, label);

        // Then
        verify(apiServiceClient, times(1)).getAllUserEmails();
        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendNewsletterToAll_WhenOneUserFails_ShouldContinueWithOthers() throws Exception {
        // Given
        when(apiServiceClient.getAllUserEmails()).thenReturn(mockUsers);
        doThrow(new RuntimeException("Email failed")).when(emailService)
                .sendHtmlMessage(eq("user2@example.com"), anyString(), anyString(), anyString());

        // When
        newsletterService.sendNewsletterToAll(subject, content, label);

        // Then
        verify(emailService, times(1)).sendHtmlMessage("user1@example.com", subject, content, label);
        verify(emailService, times(1)).sendHtmlMessage("user2@example.com", subject, content, label);
        verify(emailService, times(1)).sendHtmlMessage("user3@example.com", subject, content, label);
    }

    @Test
    void sendNewsletterToRole_Success() throws Exception {
        // Given
        UserRole role = UserRole.ADMIN;
        List<UserEmailDto> adminUsers = Arrays.asList(
                UserEmailDto.builder().id(1L).email("admin1@example.com").build(),
                UserEmailDto.builder().id(2L).email("admin2@example.com").build()
        );
        when(apiServiceClient.getUserEmailsByRole(role)).thenReturn(adminUsers);

        // When
        newsletterService.sendNewsletterToRole(role, subject, content, label);

        // Then
        verify(apiServiceClient, times(1)).getUserEmailsByRole(role);
        verify(emailService, times(adminUsers.size())).sendHtmlMessage(anyString(), eq(subject), eq(content), eq(label));
    }

    @Test
    void sendNewsletterToUser_Success() throws Exception {
        // Given
        String singleEmail = "single@example.com";

        // When
        newsletterService.sendNewsletterToUser(singleEmail, subject, content, label);

        // Then
        verify(emailService, times(1)).sendHtmlMessage(singleEmail, subject, content, label);
    }

    @Test
    void sendNewsletterToUser_WhenEmailFails_ShouldThrowException() throws Exception {
        // Given
        String singleEmail = "failing@example.com";
        doThrow(new RuntimeException("Email service down")).when(emailService)
                .sendHtmlMessage(eq(singleEmail), anyString(), anyString(), anyString());

        // When & Then
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> newsletterService.sendNewsletterToUser(singleEmail, subject, content, label));

        verify(emailService, times(1)).sendHtmlMessage(singleEmail, subject, content, label);
    }
}