package com.tunindex.market_tool.api.services.password_reset;


import com.tunindex.market_tool.api.dto.password_reset.TokenVerificationResponse;
import com.tunindex.market_tool.common.dto.auth.ChangePasswordUserRequestDto;

public interface PasswordResetService {

    void sendResetLink(String email, String recaptchaToken, String userIp, String action);
    boolean resetPassword(String token, ChangePasswordUserRequestDto newPassword);
    boolean resendResetLink(String email, String recaptchaToken, String userIp, String action);

    TokenVerificationResponse verifyToken(String token);
}
