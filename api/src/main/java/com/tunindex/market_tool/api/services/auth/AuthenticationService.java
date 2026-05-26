package com.tunindex.market_tool.api.services.auth;

import com.tunindex.market_tool.common.dto.auth.AuthCheckResponse;
import com.tunindex.market_tool.common.dto.auth.AuthenticationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import java.io.IOException;

public interface AuthenticationService {

    // OAuth2 login with Google
    AuthenticationResponse authenticateWithOAuth2(OAuth2AuthenticationToken oauthToken, HttpServletRequest request);

    // Validate existing token and issue new one
    AuthenticationResponse authenticateWithToken(String token, HttpServletRequest request);

    // Refresh token
    void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException;

    // Check if user is authenticated
    AuthCheckResponse checkUserAuthentication(String email);


}