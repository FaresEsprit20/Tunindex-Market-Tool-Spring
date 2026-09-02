package com.tunindex.market_tool.api.controllers.twofactor;

import com.tunindex.market_tool.api.dto.two_factor.TotpCodeRequestDto;
import com.tunindex.market_tool.api.dto.two_factor.TotpSetupResponseDto;
import com.tunindex.market_tool.api.dto.two_factor.TotpStatusResponseDto;
import com.tunindex.market_tool.api.services.totp.TwoFactorSetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TwoFactorSetupController implements TwoFactorSetupApi {

    private final TwoFactorSetupService twoFactorSetupService;

    @Override
    public TotpStatusResponseDto getStatus(Authentication authentication) {
        return twoFactorSetupService.getStatus(authentication);
    }

    @Override
    public TotpSetupResponseDto beginSetup(Authentication authentication) {
        return twoFactorSetupService.beginSetup(authentication);
    }

    @Override
    public ResponseEntity<Void> confirmSetup(TotpCodeRequestDto request, Authentication authentication) {
        twoFactorSetupService.confirmSetup(authentication, request.getCode());
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> disable(TotpCodeRequestDto request, Authentication authentication) {
        twoFactorSetupService.disable(authentication, request.getCode());
        return ResponseEntity.ok().build();
    }
}
