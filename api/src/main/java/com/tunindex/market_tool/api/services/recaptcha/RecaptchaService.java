package com.tunindex.market_tool.api.services.recaptcha;

public interface RecaptchaService {
    boolean validate(String recaptchaToken, String userIp, String action); // Add 'action'
}
