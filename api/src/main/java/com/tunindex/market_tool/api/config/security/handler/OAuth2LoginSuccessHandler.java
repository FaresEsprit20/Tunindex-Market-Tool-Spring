package com.tunindex.market_tool.api.config.security.handler;

import com.tunindex.market_tool.api.config.security.jwt.OAuth2TokenService;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.enums.TokenType;
import com.tunindex.market_tool.api.repository.UnifiedTokenRepository;
import com.tunindex.market_tool.api.services.users.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static com.tunindex.market_tool.common.utils.constants.Constants.PRODUCTION_ENVIRONMENT;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final CustomUserDetailsService userDetailsService;
    private final OAuth2TokenService tokenService;
    private final UnifiedTokenRepository tokenRepository;

    @Value("${app.oauth2.redirect-url:http://localhost:4200/oauth2/success}")
    private String redirectUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauthToken.getPrincipal();

        String provider = oauthToken.getAuthorizedClientRegistrationId();
        Map<String, Object> attributes = oauth2User.getAttributes();

        // Extract user info from provider
        String providerId = extractProviderId(provider, attributes);
        String email = extractEmail(provider, attributes);
        String name = extractName(provider, attributes);

        log.info("OAuth2 login success - Provider: {}, ProviderId: {}, Email: {}", provider, providerId, email);

        // Load or create user
        User user = (User) userDetailsService.loadUserByOAuth2Provider(provider, providerId, email, name);

        // Revoke all existing tokens for this user (optional - single session only)
        // tokenService.revokeAllUserTokens(user.getId());

        // Generate new opaque tokens
        String accessToken = tokenService.generateAccessToken();
        String refreshToken = tokenService.generateRefreshToken();

        // Store access token (15 minutes expiry)
        tokenService.storeToken(accessToken, TokenType.OAUTH2_ACCESS,
                user.getId(), user.getEmail(), request, 15);

        // Store refresh token (7 days expiry)
        tokenService.storeToken(refreshToken, TokenType.OAUTH2_REFRESH,
                user.getId(), user.getEmail(), request, 10080); // 7 days = 10080 minutes

        log.info("OAuth2 tokens stored for user: {}", user.getEmail());

        // Set cookies
        setAuthCookies(response, accessToken, refreshToken);

        // Build redirect URL with tokens (for frontend)
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build()
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String extractProviderId(String provider, Map<String, Object> attributes) {
        switch (provider) {
            case "google":
                return attributes.get("sub").toString();
            case "github":
                return attributes.get("id").toString();
            case "facebook":
                return attributes.get("id").toString();
            default:
                return attributes.get("sub") != null ? attributes.get("sub").toString() :
                        attributes.get("id") != null ? attributes.get("id").toString() : null;
        }
    }

    private String extractEmail(String provider, Map<String, Object> attributes) {
        String email = (String) attributes.get("email");
        if (email == null || email.isEmpty()) {
            // Generate fake email if provider doesn't provide one
            String providerId = extractProviderId(provider, attributes);
            return providerId + "@" + provider + ".com";
        }
        return email;
    }

    private String extractName(String provider, Map<String, Object> attributes) {
        String name = (String) attributes.get("name");
        if (name == null || name.isEmpty()) {
            name = (String) attributes.get("login"); // GitHub
        }
        if (name == null || name.isEmpty()) {
            name = provider + "_user";
        }
        return name;
    }

    private void setAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        // Access token cookie (15 minutes)
        ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(PRODUCTION_ENVIRONMENT)
                .path("/")
                .maxAge(Duration.ofMinutes(15))
                .sameSite("Lax")
                .build();

        // Refresh token cookie (7 days)
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(PRODUCTION_ENVIRONMENT)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        log.info("OAuth2 cookies set successfully");
    }
}