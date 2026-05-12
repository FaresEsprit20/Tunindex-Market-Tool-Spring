package com.tunindex.market_tool.mailing.service;

import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.mailing.dto.UserRole;
import com.tunindex.market_tool.mailing.repository.UserEmailRepository;
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

    private final UserEmailRepository userJdbcRepository;
    private final EmailService emailService;

    @Autowired
    public NewsletterServiceImpl(UserEmailRepository userJdbcRepository, EmailService emailService) {
        this.userJdbcRepository = userJdbcRepository;
        this.emailService = emailService;
    }

    @Override
    public void sendNewsletterToAll(String subject, String htmlContent, String label) {
        List<String> emails = userJdbcRepository.findAllUserEmails();
        emails.forEach(email -> sendEmail(email, subject, htmlContent, label));
    }

    @Override
    public void sendNewsletterToRole(UserRole role, String subject, String htmlContent, String label) {
        List<String> emails = userJdbcRepository.findEmailsByRole(role);
        emails.forEach(email -> sendEmail(email, subject, htmlContent, label));
    }

    @Override
    public void sendNewsletterToUser(String email, String subject, String htmlContent, String label) {
        sendEmail(email, subject, htmlContent, label);
    }

    // Helper method to send email
    private void sendEmail(String email, String subject, String htmlContent, String label) {
        List<String> errors = new ArrayList<>();
        try {
            emailService.sendHtmlMessage(email, subject, htmlContent, label);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.warn(e.getMessage());
            errors.add("Error sending email to " + email);
            throw new InvalidOperationException("Error sending email to " + email,
                    ErrorCodes.EMAIL_SERVICE_ERROR,errors);
        }
    }


}
