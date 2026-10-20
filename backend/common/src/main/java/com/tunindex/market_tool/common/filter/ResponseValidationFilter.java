package com.tunindex.market_tool.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Inspects responses for signs that we were served a block page instead of the
 * content we asked for.
 *
 * <p>Two things here are easy to get wrong and were both wrong before.
 *
 * <p><b>The body must be put back.</b> A {@code ClientResponse} body is a
 * one-shot stream. Reading it inside a filter and then passing the original
 * response along leaves every caller downstream holding an empty body with a
 * 200 status - which looks exactly like a site returning blank pages. That is
 * what stopped price history from loading: the antiforgery token was there in
 * the HTML, but the provider that needed it received null. Anything read here
 * is rebuilt into a new response via {@link ClientResponse#from}.
 *
 * <p><b>The markers must be specific.</b> Substring checks for "captcha" or
 * "Cloudflare" match ordinary pages: most of the web sits behind Cloudflare and
 * ships a beacon script naming it, and any page may mention a captcha in
 * passing. Broad markers here do not fail loudly - they turn a good response
 * into an error, and the scraper then looks blocked by a wall that is not
 * there. The phrases below are ones that only appear on an actual interstitial.
 */
@Slf4j
public class ResponseValidationFilter implements ExchangeFilterFunction {

    /**
     * Only the first part of a page is scanned. A real block page is small and
     * says so immediately; a content page can mention anything anywhere.
     */
    private static final int SCAN_LIMIT = 60_000;

    private static final List<String> BLOCK_PHRASES = List.of(
            "access denied",
            "your ip has been blocked",
            "you have been blocked",
            "sorry, you have been blocked",
            "attention required!",
            "please verify you are a human",
            "verify you are human",
            "checking your browser before accessing");

    private static final List<String> CHALLENGE_PHRASES = List.of(
            "cf-browser-verification",
            "/cdn-cgi/challenge-platform",
            "g-recaptcha",
            "h-captcha",
            "hcaptcha.com/captcha",
            "recaptcha/api.js");

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return next.exchange(request)
            .flatMap(response -> {
                int status = response.statusCode().value();

                if (status >= 200 && status < 300) {
                    log.debug("✅ Success response: {} for {}", status, request.url());
                    return validateResponseBody(request, response);
                }

                if (status == 403) {
                    log.warn("⛔ Forbidden access to: {}", request.url());
                    return checkForBlockingHeaders(request, response);
                }

                if (status == 429) {
                    log.warn("🚫 Rate limited: {}", request.url());
                    return handleRateLimit(request, response);
                }

                if (status >= 500) {
                    log.warn("🔴 Server error {} for: {}", status, request.url());
                    return Mono.error(new RuntimeException("Server error: " + status));
                }

                log.debug("📊 Response status: {} for {}", status, request.url());
                return Mono.just(response);
            });
    }

    private Mono<ClientResponse> validateResponseBody(ClientRequest request, ClientResponse response) {
        return response.bodyToMono(String.class)
            .flatMap(body -> {
                String scanned = body.length() > SCAN_LIMIT
                        ? body.substring(0, SCAN_LIMIT).toLowerCase()
                        : body.toLowerCase();

                String block = firstMatch(scanned, BLOCK_PHRASES);
                if (block != null) {
                    log.warn("🛡️ Block page detected for {} (matched \"{}\")", request.url(), block);
                    return Mono.error(new RuntimeException("Blocked: " + block));
                }

                String challenge = firstMatch(scanned, CHALLENGE_PHRASES);
                if (challenge != null) {
                    log.warn("🔐 Challenge page detected for {} (matched \"{}\")", request.url(), challenge);
                    return Mono.error(new RuntimeException("Challenge required: " + challenge));
                }

                if (body.isBlank()) {
                    log.warn("📭 Empty response body for: {}", request.url());
                    return Mono.error(new RuntimeException("Empty response"));
                }

                log.debug("✅ Response validation passed for: {}", request.url());
                // Rebuilt, not passed through: the original body stream has
                // just been consumed by the read above.
                return Mono.just(ClientResponse.from(response).body(body).build());
            })
            .switchIfEmpty(Mono.error(new RuntimeException("Empty response body")));
    }

    private String firstMatch(String lowercaseBody, List<String> phrases) {
        for (String phrase : phrases) {
            if (lowercaseBody.contains(phrase)) {
                return phrase;
            }
        }
        return null;
    }

    private Mono<ClientResponse> checkForBlockingHeaders(ClientRequest request, ClientResponse response) {
        // Only reached on a 403. Cloudflare fronts a large share of the web, so
        // its presence is meaningful here - where the request was actually
        // refused - and meaningless on a 200.
        String server = response.headers().header("Server")
            .stream().findFirst().orElse("");
        String cfRay = response.headers().header("CF-RAY")
            .stream().findFirst().orElse("");

        if (server.toLowerCase().contains("cloudflare") || !cfRay.isEmpty()) {
            log.warn("☁️ Cloudflare refused: {}", request.url());
            return Mono.error(new RuntimeException("Cloudflare detected"));
        }

        return Mono.error(new RuntimeException("Access forbidden"));
    }

    private Mono<ClientResponse> handleRateLimit(ClientRequest request, ClientResponse response) {
        String retryAfter = response.headers().header("Retry-After")
            .stream().findFirst().orElse("60");
        log.warn("⏰ Rate limit. Retry after: {}s", retryAfter);
        return Mono.error(new RuntimeException("Rate limited: " + retryAfter));
    }
}
