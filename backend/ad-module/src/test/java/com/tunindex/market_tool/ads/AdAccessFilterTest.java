package com.tunindex.market_tool.ads;

import com.tunindex.market_tool.ads.config.AdAccessFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pins who can reach what.
 *
 * <p>Before this filter existed, every one of these calls succeeded from an
 * anonymous request - including creating campaigns and deleting them. So the
 * cases worth writing down are the ones that must now be refused, and equally
 * the ordinary delivery calls that must keep working: a filter that locks out
 * the ad serving is not safer, it just breaks the product silently and earns
 * nothing.
 */
@DisplayName("AdAccessFilter")
class AdAccessFilterTest {

    private static final String API_KEY = "internal-key-for-tests";
    private static final String SESSION = "session-key-abc";

    private AdAccessFilter filter;
    private FilterChain chain;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new AdAccessFilter();
        ReflectionTestUtils.setField(filter, "internalApiKey", API_KEY);
        chain = mock(FilterChain.class);
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, response, chain);
    }

    private void assertPassed() throws Exception {
        verify(chain, times(1)).doFilter(any(), any());
    }

    private void assertRefused() throws Exception {
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);
    }

    // -- Management ------------------------------------------------------

    @Test
    @DisplayName("creating a campaign anonymously is refused")
    void anonymousCannotCreateCampaign() throws Exception {
        // The hole this filter closes: this exact call succeeded before.
        run(request("POST", "/api/v1/ads"));

        assertRefused();
    }

    @Test
    @DisplayName("deleting a campaign anonymously is refused")
    void anonymousCannotDeleteCampaign() throws Exception {
        run(request("DELETE", "/api/v1/ads/7"));

        assertRefused();
    }

    @Test
    @DisplayName("a session key does not buy management access")
    void sessionKeyIsNotEnoughForManagement() throws Exception {
        // Otherwise any signed-in reader could rewrite the rate card.
        MockHttpServletRequest request = request("PUT", "/api/v1/ads/7");
        request.addHeader("X-Session-Key", SESSION);

        run(request);

        assertRefused();
    }

    @Test
    @DisplayName("revenue is not readable without the internal key")
    void revenueRequiresApiKey() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/ads/revenue");
        request.addHeader("X-Session-Key", SESSION);

        run(request);

        assertRefused();
    }

    @Test
    @DisplayName("the internal key opens management")
    void apiKeyOpensManagement() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/ads");
        request.addHeader("X-API-Key", API_KEY);

        run(request);

        assertPassed();
    }

    @Test
    @DisplayName("a wrong internal key is refused")
    void wrongApiKeyIsRefused() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/ads");
        request.addHeader("X-API-Key", "not-the-key");

        run(request);

        assertRefused();
    }

    // -- Delivery --------------------------------------------------------

    @Test
    @DisplayName("serving an ad works for a signed-in viewer")
    void deliveryWorksWithSession() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/ads/serve/DASHBOARD_TOP");
        request.addHeader("X-Session-Key", SESSION);

        run(request);

        assertPassed();
    }

    @Test
    @DisplayName("reporting an impression works for a signed-in viewer")
    void eventReportingWorksWithSession() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/ads/3/events");
        request.addHeader("X-Session-Key", SESSION);

        run(request);

        assertPassed();
    }

    @Test
    @DisplayName("the gate flow works for a signed-in viewer")
    void gateWorksWithSession() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/ads/gate/start/PIPELINE_RUN");
        request.addHeader("X-Session-Key", SESSION);

        run(request);

        assertPassed();
    }

    @Test
    @DisplayName("serving an ad anonymously is refused")
    void deliveryNeedsASession() throws Exception {
        run(request("GET", "/api/v1/ads/serve/DASHBOARD_TOP"));

        assertRefused();
    }

    @Test
    @DisplayName("reading one campaign is management, not delivery")
    void readingACampaignIsManagement() throws Exception {
        // GET /ads/{id} returns the rate and budget. The verb makes it look
        // harmless; the payload is not.
        MockHttpServletRequest request = request("GET", "/api/v1/ads/3");
        request.addHeader("X-Session-Key", SESSION);

        run(request);

        assertRefused();
    }

    // -- Everything else -------------------------------------------------

    @Test
    @DisplayName("health checks are left alone")
    void actuatorIsUntouched() throws Exception {
        // Guarded by path prefix, so anything outside /api/v1/ads must pass
        // through untouched - including the probes that keep the service in
        // service discovery.
        run(request("GET", "/actuator/health"));

        assertPassed();
    }

    @Test
    @DisplayName("a CORS preflight is not refused")
    void preflightPasses() throws Exception {
        // Preflights carry no credentials by design; refusing one fails the
        // real request before the browser ever sends it.
        run(request("OPTIONS", "/api/v1/ads/serve/DASHBOARD_TOP"));

        assertPassed();
    }
}
