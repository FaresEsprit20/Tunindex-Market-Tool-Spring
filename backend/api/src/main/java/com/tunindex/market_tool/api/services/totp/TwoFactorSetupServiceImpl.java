package com.tunindex.market_tool.api.services.totp;

import com.tunindex.market_tool.api.dto.two_factor.TotpSetupResponseDto;
import com.tunindex.market_tool.api.dto.two_factor.TotpStatusResponseDto;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.repository.UserRepository;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorSetupServiceImpl implements TwoFactorSetupService {

    private final UserRepository userRepository;
    private final TotpService totpService;

    @Override
    @Transactional
    public TotpSetupResponseDto beginSetup(Authentication authentication) {
        User user = resolveUser(authentication);

        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new InvalidOperationException(
                    "Two-factor authentication is already enabled",
                    ErrorCodes.TOTP_ALREADY_ENABLED,
                    List.of("Disable it first to set it up again with a new device"));
        }

        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);

        log.info("TOTP setup started for user: {}", user.getEmail());
        return TotpSetupResponseDto.builder()
                .secret(secret)
                .otpAuthUri(totpService.buildOtpAuthUri(secret, user.getEmail()))
                .build();
    }

    @Override
    @Transactional
    public void confirmSetup(Authentication authentication, String code) {
        User user = resolveUser(authentication);

        if (user.getTotpSecret() == null) {
            throw new InvalidOperationException(
                    "No two-factor setup in progress",
                    ErrorCodes.TOTP_SETUP_NOT_STARTED,
                    List.of("Start setup first before confirming a code"));
        }

        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new InvalidOperationException(
                    "Invalid authentication code",
                    ErrorCodes.TWO_FACTOR_TOKEN_INVALID,
                    List.of("The code you entered is incorrect or has expired"));
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        log.info("TOTP enabled for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void disable(Authentication authentication, String code) {
        User user = resolveUser(authentication);

        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new InvalidOperationException(
                    "Two-factor authentication is not enabled",
                    ErrorCodes.TOTP_NOT_ENABLED,
                    Collections.emptyList());
        }

        if (!totpService.verifyCode(user.getTotpSecret(), code)) {
            throw new InvalidOperationException(
                    "Invalid authentication code",
                    ErrorCodes.TWO_FACTOR_TOKEN_INVALID,
                    List.of("The code you entered is incorrect or has expired"));
        }

        user.setTwoFactorEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        log.info("TOTP disabled for user: {}", user.getEmail());
    }

    @Override
    public TotpStatusResponseDto getStatus(Authentication authentication) {
        User user = resolveUser(authentication);
        return TotpStatusResponseDto.builder()
                .enabled(Boolean.TRUE.equals(user.getTwoFactorEnabled()))
                .build();
    }

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidEntityException("Not authenticated", ErrorCodes.USER_NOT_AUTHENTICATED, Collections.emptyList());
        }
        String email = authentication.getName();
        return userRepository.findUserByEmail(email)
                .orElseThrow(() -> new InvalidEntityException(
                        "User not found", ErrorCodes.USER_NOT_FOUND, Collections.singletonList("email: " + email)));
    }
}
