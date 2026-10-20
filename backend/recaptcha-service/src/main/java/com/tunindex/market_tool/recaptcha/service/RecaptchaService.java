package com.tunindex.market_tool.recaptcha.service;

public interface RecaptchaService {
    boolean validate(String recaptchaToken, String userIp, String action); // Add 'action'
}
