package com.tunindex.market_tool.mailing.service;


import com.tunindex.market_tool.mailing.dto.UserRole;

public interface NewsletterService {

    void sendNewsletterToAll(String subject, String htmlContent, String label);

    void sendNewsletterToRole(UserRole role, String subject, String htmlContent, String label);

    void sendNewsletterToUser(String email, String subject, String htmlContent, String label);
}