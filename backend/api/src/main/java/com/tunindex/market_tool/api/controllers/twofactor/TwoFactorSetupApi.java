package com.tunindex.market_tool.api.controllers.twofactor;

import com.tunindex.market_tool.api.dto.two_factor.TotpCodeRequestDto;
import com.tunindex.market_tool.api.dto.two_factor.TotpSetupResponseDto;
import com.tunindex.market_tool.api.dto.two_factor.TotpStatusResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Two-Factor Auth", description = "TOTP-based two-factor auth setup, scoped to the current user and off by default")
public interface TwoFactorSetupApi {

    String TWO_FACTOR_ENDPOINT = APP_ROOT + "/account/2fa";

    @GetMapping(value = TWO_FACTOR_ENDPOINT + "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Whether the current user has TOTP two-factor auth enabled")
    TotpStatusResponseDto getStatus(Authentication authentication);

    @PostMapping(value = TWO_FACTOR_ENDPOINT + "/setup", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start TOTP setup: generates a new secret and QR code URI (not yet enabled)")
    TotpSetupResponseDto beginSetup(Authentication authentication);

    @PostMapping(value = TWO_FACTOR_ENDPOINT + "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirm TOTP setup with a code from the authenticator app to finish enabling it")
    ResponseEntity<Void> confirmSetup(@RequestBody TotpCodeRequestDto request, Authentication authentication);

    @PostMapping(value = TWO_FACTOR_ENDPOINT + "/disable", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Disable two-factor auth, given a valid current code")
    ResponseEntity<Void> disable(@RequestBody TotpCodeRequestDto request, Authentication authentication);
}
