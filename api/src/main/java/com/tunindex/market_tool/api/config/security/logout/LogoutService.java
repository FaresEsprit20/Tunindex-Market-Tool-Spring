package com.tunindex.market_tool.api.config.security.logout;

import com.tunindex.market_tool.api.repository.JwtTokenRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Service;

import static com.tunindex.market_tool.common.utils.constants.Constants.PRODUCTION_ENVIRONMENT;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutHandler {

    private final JwtTokenRepository tokenRepository;


    @Override
    public void logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        // Extract JWT token
        String jwt = extractTokenFromCookie(request);

                // Retrieve stored token from the repository
          var storedToken = tokenRepository.findByToken(jwt).orElse(null);
        if (storedToken != null) {
//            storedToken.setExpired(true);
//            storedToken.setRevoked(true);
//            tokenRepository.save(storedToken);  // Mark token as expired and revoked
            String userEmail = authentication.getName();
               tokenRepository.deleteAllByUserEmail(userEmail);
            if (authentication.getPrincipal() instanceof UserDetails userDetails) {
                log.warn("Logging out user with email: {}", userDetails.getUsername());
            }

            // Directly delete the access token cookie by setting its max age to 0
            deleteCookie(response, "accessToken");
            deleteCookie(response, "refreshToken");
            deleteCookie(response, "JSESSIONID");

            // Invalidate the session
            if (request.getSession() != null) {
                request.getSession().invalidate();
            }
            // Clear security context
            SecurityContextHolder.clearContext();
            deleteCookie(response, "JSESSIONID");
        }



    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String cookieName = "accessToken";
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void deleteCookie(HttpServletResponse response, String cookieName) {
        response.addCookie(new Cookie(cookieName, null) {{
            setPath("/");  // Ensure path matches the one used for setting the cookie
            setHttpOnly(true);  // Make sure it is HTTP-only
            setSecure(PRODUCTION_ENVIRONMENT);  // Secure cookie for production
            setMaxAge(0);  // Expire the cookie immediately
        }});
    }


}
