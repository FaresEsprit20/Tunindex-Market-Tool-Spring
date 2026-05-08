package com.tunindex.market_tool.common.controllers.auth;

import com.tunindex.market_tool.common.dto.auth.AuthCheckResponse;
import com.tunindex.market_tool.common.dto.auth.AuthenticationRequest;
import com.tunindex.market_tool.common.dto.auth.AuthenticationResponse;
import com.tunindex.market_tool.common.services.auth.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthenticationController implements AuthenticationApi {

    private final AuthenticationService authenticationService;

    @Override
    public ResponseEntity<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        log.info("📝 Authentication attempt for user: {}", request.getLogin());

        AuthenticationResponse authResponse = authenticationService.authenticate(request, httpRequest);

        log.info("✅ Authentication successful for user: {}", request.getLogin());

        return ResponseEntity.ok(authResponse);
    }

    @Override
    public ResponseEntity<AuthCheckResponse> checkAuthentication(Authentication authentication) {
        log.info("🔍 /check-auth called");

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("❌ User NOT authenticated - returning false");
            return ResponseEntity.ok(new AuthCheckResponse(false, null, null));
        }

        String email = authentication.getName();
        log.info("✅ User IS authenticated: {}", email);

        AuthCheckResponse response = authenticationService.checkUserAuthentication(email);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        log.info("🔄 Refresh token request");
        try {
            authenticationService.refreshToken(request, response);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("❌ Refresh token failed: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }
}