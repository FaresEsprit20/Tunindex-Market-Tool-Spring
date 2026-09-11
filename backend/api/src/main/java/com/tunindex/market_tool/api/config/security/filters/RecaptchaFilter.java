package com.tunindex.market_tool.api.config.security.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Bot protection on the handful of endpoints bots actually go after.
 *
 * <p>This class existed but was commented out in its entirety, so nothing was
 * protected. Re-enabling it as written would have broken the application
 * immediately: it demanded a token on <em>every</em> POST, PUT, PATCH and
 * DELETE, and the frontend sent none at all - so every paper trade, every
 * watchlist star and every profile edit would have failed with "missing
 * recaptcha token".
 *
 * <p>So the rule is now an explicit list instead of "all writes". Credential
 * stuffing, mass sign-ups and password-reset flooding are what reCAPTCHA is
 * for; an in-app action already behind a session gains nothing from a second
 * check and would only acquire a new way to fail.
 *
 * <p><b>On failure it lets the request through.</b> That is a deliberate
 * choice and worth stating plainly: reCAPTCHA sits between a user and their
 * own account, and Google being unreachable is not a reason to lock every
 * customer out of signing in. The endpoints behind it keep their own
 * defences - password checks, lockouts, the gateway's rate limit - so an
 * outage here costs bot resistance, not security. Set
 * {@code recaptcha.fail-closed=true} to invert that where availability
 * matters less than the bot risk.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RecaptchaFilter extends OncePerRequestFilter {

    private final WebClient.Builder webClientBuilder;

    @Value("${recaptcha.service.url:http://recaptcha-service}")
    private String recaptchaServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Value("${recaptcha.enabled:true}")
    private boolean enabled;

    /** Whether an unverifiable token blocks the request. See the class note. */
    @Value("${recaptcha.fail-closed:false}")
    private boolean failClosed;

    /**
     * The endpoints a bot targets, and the only ones that require a token.
     *
     * <p>Matched on the full path. Keeping this short is the point - every
     * entry added is another call that breaks when Google is unreachable.
     */
    private static final List<String> PROTECTED_PATHS = List.of(
            "/auth/authenticate",
            "/auth/two-factor/verify",
            "/users/create",
            "/accounts/management/user/create",
            "/password-reset"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        if (!enabled || !isProtected(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader("X-Recaptcha-Token");
        if (token == null || token.isBlank()) {
            log.warn("No reCAPTCHA token on {} {}", request.getMethod(), path);
            if (failClosed) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing reCAPTCHA token");
                return;
            }
            // An older client, a blocked script, or a browser extension. The
            // endpoint's own checks still apply.
            filterChain.doFilter(request, response);
            return;
        }

        Boolean valid = validate(token, clientIp(request), actionFor(path));

        if (Boolean.FALSE.equals(valid)) {
            // A definite rejection from Google: the token was forged, replayed
            // or scored as a bot. This is refused whatever the failure policy,
            // because it is not an outage - it is the answer.
            log.warn("reCAPTCHA rejected {} {}", request.getMethod(), path);
            sendError(response, HttpServletResponse.SC_UNAUTHORIZED, "Failed bot check");
            return;
        }

        if (valid == null && failClosed) {
            // Could not reach the verifier at all.
            sendError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Bot check unavailable, please retry");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * @return true when verified, false when Google rejected it, and null when
     *         the check could not be carried out at all
     *
     * <p>Three outcomes rather than two, because collapsing "rejected" into
     * "unavailable" is what makes a fail-open policy dangerous: it would let a
     * forged token through whenever the service hiccuped.
     */
    private Boolean validate(String token, String userIp, String action) {
        try {
            Map<String, Object> body = webClientBuilder.build()
                    .post()
                    .uri(recaptchaServiceUrl + "/internal/recaptcha/validate")
                    .header("X-API-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "token", token,
                            "userIp", userIp == null ? "" : userIp,
                            "action", action))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (body == null) {
                return null;
            }
            return Boolean.TRUE.equals(body.get("success"));
        } catch (Exception e) {
            log.error("reCAPTCHA service unreachable: {}", e.getMessage());
            return null;
        }
    }

    private boolean isProtected(String path) {
        return PROTECTED_PATHS.stream().anyMatch(path::contains);
    }

    /** Scored separately per action, so abuse of one flow is visible. */
    private String actionFor(String path) {
        if (path.contains("/auth/authenticate")) return "login";
        if (path.contains("/auth/two-factor")) return "two_factor";
        if (path.contains("/password-reset")) return "password_reset";
        if (path.contains("create")) return "register";
        return "submit";
    }

    /**
     * The caller's address, as the gateway reported it.
     *
     * <p>The gateway overwrites these headers from the real socket and refuses
     * to believe a client's own, so what arrives here is trustworthy - see
     * ClientAddressFilter on the gateway.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp != null && !realIp.isBlank() ? realIp : request.getRemoteAddr();
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(Map.of(
                "success", false,
                "error", message,
                "timestamp", System.currentTimeMillis())));
    }
}
