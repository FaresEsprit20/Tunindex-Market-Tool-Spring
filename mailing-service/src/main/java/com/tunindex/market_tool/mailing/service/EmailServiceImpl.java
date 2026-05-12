package com.tunindex.market_tool.mailing.service;

import com.tunindex.market_tool.mailing.entity.EmailLog;
import com.tunindex.market_tool.mailing.repository.EmailLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;
    private final String mailFrom = "faresbenslama95@gmail.com";

    @Override
    @Async("mailExecutor")
    public void sendSimpleMessage(String to, String subject, String text, String label) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            saveEmailLog(to, subject, "SIMPLE", "SUCCESS");
            log.info("Email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            saveEmailLog(to, subject, "SIMPLE", "FAILED");
        }
    }

    @Override
    @Async("mailExecutor")
    public void sendHtmlMessage(String to, String subject, String htmlContent, String label) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailFrom, label);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
        saveEmailLog(to, subject, "HTML", "SUCCESS");
        log.info("HTML email sent to: {}", to);
    }

    @Override
    public void sendTwoFactorAuthEmail(String to, String otp) throws MessagingException, UnsupportedEncodingException {
        String subject = "Your 2FA Code - Market Tool";
        String html = "<html><body><h2>Your OTP Code</h2><h3>" + otp + "</h3><p>Valid for 3 minutes</p></body></html>";
        sendHtmlMessage(to, subject, html, "Market Tool Security");
    }

    private void saveEmailLog(String to, String subject, String type, String status) {
        try {
            EmailLog log = EmailLog.builder()
                    .recipient(to).subject(subject).emailType(type).status(status).sentAt(LocalDateTime.now()).build();
            emailLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save email log", e);
        }
    }
}
