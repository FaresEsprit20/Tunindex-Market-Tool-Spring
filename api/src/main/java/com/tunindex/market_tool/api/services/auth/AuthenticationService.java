package com.tunindex.market_tool.api.services.auth;

import com.tunindex.market_tool.common.dto.auth.AuthCheckResponse;
import com.tunindex.market_tool.common.dto.auth.AuthenticationRequest;
import com.tunindex.market_tool.common.dto.auth.AuthenticationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface AuthenticationService {


    AuthenticationResponse authenticate(AuthenticationRequest authRequest, HttpServletRequest request);

    void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException;

    AuthCheckResponse checkUserAuthentication(String email);
}
