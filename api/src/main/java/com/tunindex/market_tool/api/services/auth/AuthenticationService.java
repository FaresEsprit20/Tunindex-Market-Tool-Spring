package com.tunindex.market_tool.api.services.auth;


import com.fares.stock.management.domain.dto.auth.AuthCheckResponse;
import com.fares.stock.management.domain.dto.two_factor.AuthenticationRequestMfoDto;
import com.fares.stock.management.domain.dto.two_factor.AuthenticationTfoDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface AuthenticationService {


    AuthenticationTfoDto authenticate(AuthenticationRequestMfoDto request, HttpServletRequest httpServletRequest);

    AuthenticationTfoDto generatePostMfaTokens(String userEmail, HttpServletRequest request);

    void refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException;


    AuthCheckResponse checkUserAuthentication(String email);
}
