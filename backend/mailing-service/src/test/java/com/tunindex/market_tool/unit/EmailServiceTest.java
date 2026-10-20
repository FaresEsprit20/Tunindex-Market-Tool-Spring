package com.tunindex.market_tool.unit;

import com.tunindex.market_tool.mailing.entity.EmailLog;
import com.tunindex.market_tool.mailing.repository.EmailLogRepository;
import com.tunindex.market_tool.mailing.service.EmailServiceImpl;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailServiceImpl emailService;

    private final String testEmail = "test@example.com";
    private final String testSubject = "Test Subject";
    private final String testContent = "Test Content";
    private final String testLabel = "Test Label";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "mailFrom", "noreply@market-tool.com");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendSimpleMessage_Success() {
        // Given
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);

        // When
        emailService.sendSimpleMessage(testEmail, testSubject, testContent, testLabel);

        // Then
        verify(mailSender, times(1)).send(messageCaptor.capture());
        verify(emailLogRepository, times(1)).save(logCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        Assertions.assertNotNull(sentMessage.getTo());
        assertThat(sentMessage.getTo()[0]).isEqualTo(testEmail);
        assertThat(sentMessage.getSubject()).isEqualTo(testSubject);
        assertThat(sentMessage.getText()).isEqualTo(testContent);

        EmailLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getRecipient()).isEqualTo(testEmail);
        assertThat(savedLog.getSubject()).isEqualTo(testSubject);
        assertThat(savedLog.getEmailType()).isEqualTo("SIMPLE");
        assertThat(savedLog.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void sendSimpleMessage_WhenException_ShouldSaveFailedLog() {
        // Given
        doThrow(new RuntimeException("Mail server error")).when(mailSender).send(any(SimpleMailMessage.class));
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);

        // When & Then
        assertThatThrownBy(() -> emailService.sendSimpleMessage(testEmail, testSubject, testContent, testLabel))
                .isInstanceOf(RuntimeException.class);

        verify(emailLogRepository, times(1)).save(logCaptor.capture());
        EmailLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void sendHtmlMessage_Success() throws Exception {
        // Given
        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);

        // When
        emailService.sendHtmlMessage(testEmail, testSubject, testContent, testLabel);

        // Then
        verify(mailSender, times(1)).send(messageCaptor.capture());
        verify(emailLogRepository, times(1)).save(logCaptor.capture());

        EmailLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getRecipient()).isEqualTo(testEmail);
        assertThat(savedLog.getSubject()).isEqualTo(testSubject);
        assertThat(savedLog.getEmailType()).isEqualTo("HTML");
        assertThat(savedLog.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void sendTwoFactorAuthEmail_Success() throws Exception {
        // Given
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

        // When
        String testOtp = "123456";
        emailService.sendTwoFactorAuthEmail(testEmail, testOtp);

        // Then
        verify(emailService, times(1)).sendHtmlMessage(
                emailCaptor.capture(),
                subjectCaptor.capture(),
                contentCaptor.capture(),
                any(String.class)
        );

        assertThat(emailCaptor.getValue()).isEqualTo(testEmail);
        assertThat(subjectCaptor.getValue()).contains("Two-Factor Authentication");
        assertThat(contentCaptor.getValue()).contains(testOtp);
    }
}