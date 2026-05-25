//package com.tunindex.market_tool.api.config.security.filters;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.core.Ordered;
//import org.springframework.core.annotation.Order;
//import org.springframework.http.HttpMethod;
//import org.springframework.http.MediaType;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.OncePerRequestFilter;
//import org.springframework.web.reactive.function.client.WebClient;
//
//import java.io.IOException;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//@Component
//@RequiredArgsConstructor
//@Slf4j
//@Order(Ordered.HIGHEST_PRECEDENCE + 1)
//public class RecaptchaFilter extends OncePerRequestFilter {
//
//    private final WebClient.Builder webClientBuilder;
//
//    @Value("${recaptcha.service.url:http://recaptcha-service}")
//    private String recaptchaServiceUrl;
//
//    @Value("${internal.api.key}")
//    private String internalApiKey;
//
//    // Paths that should bypass recaptcha validation (internal endpoints)
//    private static final List<String> BYPASS_PATHS = List.of(
//            "/internal/",
//            "/actuator/",
//            "/v3/api-docs",
//            "/swagger-ui",
//            "/login/oauth2",
//            "/oauth2"
//    );
//
//    // Methods that require recaptcha validation
//    private static final Set<String> METHODS_REQUIRING_RECAPTCHA = Set.of(
//            "POST", "PUT", "PATCH", "DELETE"
//    );
//
//    // Paths that ALWAYS require recaptcha (even if internal)
//    private static final List<String> FORCE_RECAPTCHA_PATHS = List.of(
//            "/auth/login",
//            "/auth/register",
//            "/auth/reset-password",
//            "/accounts/management"
//    );
//
//    @Override
//    protected void doFilterInternal(
//            HttpServletRequest request,
//            HttpServletResponse response,
//            FilterChain filterChain
//    ) throws ServletException, IOException {
//
//        String method = request.getMethod();
//        String path = request.getServletPath();
//
//        log.debug("RecaptchaFilter processing: {} {}", method, path);
//
//        // Skip recaptcha for GET requests
//        if (HttpMethod.GET.matches(method)) {
//            log.debug("GET request - skipping recaptcha validation");
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        // Skip recaptcha for bypass paths (internal endpoints)
//        if (shouldBypass(path)) {
//            log.debug("Bypass path - skipping recaptcha validation");
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        // Force recaptcha for specific paths
//        if (!METHODS_REQUIRING_RECAPTCHA.contains(method) && !shouldForceRecaptcha(path)) {
//            log.debug("Method {} doesn't require recaptcha", method);
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        // Extract recaptcha token from header
//        String recaptchaToken = request.getHeader("X-Recaptcha-Token");
//        String userIp = getClientIp(request);
//        String action = extractActionFromPath(path);
//
//        if (recaptchaToken == null || recaptchaToken.isEmpty()) {
//            log.warn("Missing recaptcha token for {} {}", method, path);
//            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing recaptcha token");
//            return;
//        }
//
//        // Validate recaptcha with recaptcha service
//        boolean isValid = validateRecaptcha(recaptchaToken, userIp, action);
//
//        if (!isValid) {
//            log.warn("Recaptcha validation failed for {} {}", method, path);
//            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Recaptcha validation failed");
//            return;
//        }
//
//        log.info("Recaptcha validation successful for {} {}", method, path);
//        filterChain.doFilter(request, response);
//    }
//
//    private boolean validateRecaptcha(String token, String userIp, String action) {
//        try {
//            Map<String, String> requestBody = Map.of(
//                    "token", token,
//                    "userIp", userIp != null ? userIp : "",
//                    "action", action
//            );
//
//            Map<String, Object> response = webClientBuilder.build()
//                    .post()
//                    .uri(recaptchaServiceUrl + "/internal/recaptcha/validate")
//                    .header("X-API-Key", internalApiKey)
//                    .contentType(MediaType.APPLICATION_JSON)
//                    .bodyValue(requestBody)
//                    .retrieve()
//                    .bodyToMono(Map.class)
//                    .block();
//
//            return response != null && Boolean.TRUE.equals(response.get("success"));
//
//        } catch (Exception e) {
//            log.error("Error calling recaptcha service: {}", e.getMessage());
//            return false;
//        }
//    }
//
//    private boolean shouldBypass(String path) {
//        return BYPASS_PATHS.stream().anyMatch(path::startsWith);
//    }
//
//    private boolean shouldForceRecaptcha(String path) {
//        return FORCE_RECAPTCHA_PATHS.stream().anyMatch(path::contains);
//    }
//
//    private String extractActionFromPath(String path) {
//        // Extract action name from path (e.g., /auth/login -> login)
//        String[] parts = path.split("/");
//        if (parts.length > 0) {
//            String lastPart = parts[parts.length - 1];
//            if (!lastPart.isEmpty()) {
//                return lastPart;
//            }
//        }
//        return "generic";
//    }
//
//    private String getClientIp(HttpServletRequest request) {
//        String xForwardedFor = request.getHeader("X-Forwarded-For");
//        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
//            return xForwardedFor.split(",")[0].trim();
//        }
//        String xRealIp = request.getHeader("X-Real-IP");
//        if (xRealIp != null && !xRealIp.isEmpty()) {
//            return xRealIp;
//        }
//        return request.getRemoteAddr();
//    }
//
//    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
//        response.setStatus(status);
//        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
//        response.setCharacterEncoding("UTF-8");
//
//        Map<String, Object> errorResponse = Map.of(
//                "success", false,
//                "error", message,
//                "timestamp", System.currentTimeMillis()
//        );
//
//        response.getWriter().write(new ObjectMapper().writeValueAsString(errorResponse));
//    }
//
//}