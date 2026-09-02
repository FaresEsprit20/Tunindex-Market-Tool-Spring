package com.tunindex.market_tool.api.services.totp;

import com.tunindex.market_tool.api.dto.two_factor.TotpSetupResponseDto;
import com.tunindex.market_tool.api.dto.two_factor.TotpStatusResponseDto;
import org.springframework.security.core.Authentication;

public interface TwoFactorSetupService {

    TotpSetupResponseDto beginSetup(Authentication authentication);

    void confirmSetup(Authentication authentication, String code);

    void disable(Authentication authentication, String code);

    TotpStatusResponseDto getStatus(Authentication authentication);
}
