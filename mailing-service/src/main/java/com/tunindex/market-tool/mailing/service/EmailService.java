package com.tunindex.market_tool.mailing.service;

import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;

public interface EmailService {
    void sendSimpleMessage(String to, String subject, String text, String label);
    void sendHtmlMessage(String to, String subject, String htmlContent, String label) throws MessagingException, UnsupportedEncodingException;
    void sendTwoFactorAuthEmail(String to, String otp) throws MessagingException, UnsupportedEncodingException;
}
