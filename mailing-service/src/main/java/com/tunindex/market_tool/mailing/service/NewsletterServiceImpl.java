package com.tunindex.market_tool.mailing.service;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.mailing.dto.UserEmailDto;
import com.tunindex.market_tool.mailing.dto.UserRole;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class NewsletterServiceImpl implements NewsletterService {

    private final ApiServiceClient apiServiceClient;
    private final EmailService emailService;

    @Autowired
    public NewsletterServiceImpl(ApiServiceClient apiServiceClient, EmailService emailService) {
        this.apiServiceClient = apiServiceClient;
        this.emailService = emailService;
    }

    @Override
    public void sendNewsletterToAll(String subject, String htmlContent, String label) {
        log.info("Sending newsletter to ALL users");
        List<UserEmailDto> users = apiServiceClient.getAllUserEmails();

        for (UserEmailDto user : users) {
            sendEmail(user.getEmail(), subject, htmlContent, label);
        }
    }

    @Override
    public void sendNewsletterToRole(UserRole role, String subject, String htmlContent, String label) {
        log.info("Sending newsletter to users with role: {}", role);
        List<UserEmailDto> users = apiServiceClient.getUserEmailsByRole(role);

        for (UserEmailDto user : users) {
            sendEmail(user.getEmail(), subject, htmlContent, label);
        }
    }

    @Override
    public void sendNewsletterToUser(String email, String subject, String htmlContent, String label) {
        log.info("Sending newsletter to single user: {}", email);
        sendEmail(email, subject, htmlContent, label);
    }

    // Helper method to send email
    private void sendEmail(String email, String subject, String htmlContent, String label) {
        List<String> errors = new ArrayList<>();
        try {
            emailService.sendHtmlMessage(email, subject, htmlContent, label);
            log.debug("Email sent successfully to: {}", email);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Error sending email to {}: {}", email, e.getMessage());
            errors.add("Error sending email to " + email);
            throw new InvalidOperationException("Error sending email to " + email,
                    ErrorCodes.EMAIL_SERVICE_ERROR, errors);
        }
    }

}