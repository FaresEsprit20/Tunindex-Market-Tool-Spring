package com.tunindex.market_tool.api.services.two_facor;

import com.tunindex.market_tool.common.exception.InvalidOperationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

public interface TwoFactorAuthService {

    @Transactional
    String generateAndSendOtp(String userEmail);

    @Transactional
    boolean verifyOtp(String userEmail, String otp);

    String getUserEmailByVerificationToken(String verificationToken);

    @Transactional
    void clearExpiredTokens();

    @Transactional
    String resendOtp(String userEmail) throws InvalidOperationException;

    @Scheduled(cron = "0 0/30 * * * ?")
    void scheduledTokenCleanup();

    Map<String, Long> getTimingInfo(String userEmail);

}
