package com.tunindex.market_tool.api.controllers.auth;

import com.tunindex.market_tool.common.dto.auth.AuthCheckResponse;
import com.tunindex.market_tool.common.dto.auth.AuthenticationRequest;
import com.tunindex.market_tool.common.dto.auth.AuthenticationResponse;
import com.tunindex.market_tool.api.services.auth.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthenticationController implements AuthenticationApi {

    private final AuthenticationService authenticationService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String googleRedirectUri;

    @Override
    public ResponseEntity<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        log.info("📝 Authentication attempt with login type: {}", request.getLogin());

        String login = request.getLogin();
        String credential = request.getPassword();

        AuthenticationResponse authResponse;

        // Route to correct service method based on login type
        if ("google".equalsIgnoreCase(login)) {
            // For Google OAuth2, we need to exchange the code
            // This would typically be handled by OAuth2LoginSuccessHandler
            // For direct REST API, you'd need to implement code exchange
            authResponse = handleGoogleCodeExchange(credential, httpRequest);
        } else if ("token".equalsIgnoreCase(login)) {
            authResponse = authenticationService.authenticateWithToken(credential, httpRequest);
        } else if ("refresh".equalsIgnoreCase(login)) {
            // Handle refresh token - call refresh endpoint instead
            throw new RuntimeException("Use /refresh-token endpoint for token refresh");
        } else {
            throw new RuntimeException("Invalid login type. Use: 'google', 'token', or 'refresh'");
        }

        log.info("✅ Authentication successful for login: {}", request.getLogin());

        return ResponseEntity.ok(authResponse);
    }

    private AuthenticationResponse handleGoogleCodeExchange(String authorizationCode, HttpServletRequest request) {
        // This exchanges Google authorization code for your opaque tokens
        // You need to implement this method or use OAuth2LoginSuccessHandler
        throw new RuntimeException("Google code exchange not implemented. Use OAuth2 login flow through browser.");
    }

    @Override
    public ResponseEntity<?> getGoogleLoginUrl() {
        String loginUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=" + googleClientId +
                "&redirect_uri=" + googleRedirectUri +
                "&response_type=code" +
                "&scope=email%20profile" +
                "&access_type=offline";

        return ResponseEntity.ok(Map.of(
                "login_url", loginUrl,
                "message", "Open this URL in a browser, then use the authorization code with login='google'"
        ));
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