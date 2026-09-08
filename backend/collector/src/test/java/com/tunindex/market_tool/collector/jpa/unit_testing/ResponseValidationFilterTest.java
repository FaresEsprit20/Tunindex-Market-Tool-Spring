package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.common.filter.ResponseValidationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the two failure modes this filter has had.
 *
 * <p>The first is silent and was the more damaging: reading the body to
 * inspect it, then handing the caller the same consumed response. Nothing
 * errors - callers just receive 200 with nothing in it. Price history stopped
 * loading entirely because the antiforgery token sat in an HTML body that
 * arrived null.
 *
 * <p>The second is the opposite: markers broad enough to match ordinary pages,
 * turning good responses into errors and making the scraper look walled off.
 * The sites we scrape sit behind Cloudflare, so a bare "cloudflare" substring
 * matches their normal output.
 */
@DisplayName("ResponseValidationFilter")
class ResponseValidationFilterTest {

    private final ResponseValidationFilter filter = new ResponseValidationFilter();

    private ClientRequest request() {
        return ClientRequest.create(org.springframework.http.HttpMethod.GET,
                URI.create("https://www.ilboursa.com/marches/download/BT")).build();
    }

    private ExchangeFunction returning(ClientResponse response) {
        return req -> Mono.just(response);
    }

    private String pageOf(String body) {
        return "<html><head><title>Cotations</title></head><body>" + body + "</body></html>";
    }

    @Test
    @DisplayName("passes the body through so callers can still read it")
    void bodySurvivesValidation() {
        String html = pageOf("<input name=\"__RequestVerificationToken\" value=\"CfDJ8ABC\" />");
        ClientResponse response = ClientResponse.create(HttpStatus.OK).body(html).build();

        ClientResponse filtered = filter.filter(request(), returning(response)).block();

        assertThat(filtered).isNotNull();
        // The whole point: a second read must still yield the document.
        assertThat(filtered.bodyToMono(String.class).block()).isEqualTo(html);
    }

    @Test
    @DisplayName("preserves Set-Cookie when rebuilding the response")
    void preservesHeaders() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header("Set-Cookie", ".AspNetCore.Antiforgery.5RYU4=CfDJ8xyz; path=/; httponly")
                .body(pageOf("ok"))
                .build();

        ClientResponse filtered = filter.filter(request(), returning(response)).block();

        assertThat(filtered).isNotNull();
        // The antiforgery flow needs the cookie and the token together; losing
        // the header on rebuild would break the POST just as surely as losing
        // the body broke the GET.
        assertThat(filtered.headers().header("Set-Cookie"))
                .anyMatch(cookie -> cookie.startsWith(".AspNetCore.Antiforgery"));
    }

    @Test
    @DisplayName("does not flag an ordinary page served through Cloudflare")
    void allowsCloudflareFrontedContent() {
        // Every page these sites serve carries a beacon like this. Treating it
        // as a challenge would fail every request to a healthy site.
        String html = pageOf("<script defer src=\"/cdn-cgi/scripts/beacon.min.js\" "
                + "data-cf-beacon='{\"token\":\"abc\"}'></script>"
                + "<p>Cotations fournies par Cloudflare-hosted ilboursa</p>");
        ClientResponse response = ClientResponse.create(HttpStatus.OK).body(html).build();

        assertThat(filter.filter(request(), returning(response)).block()).isNotNull();
    }

    @Test
    @DisplayName("errors on a genuine block page")
    void rejectsBlockPage() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .body("<html><title>Attention Required!</title>"
                        + "<body>Sorry, you have been blocked</body></html>")
                .build();

        assertThatThrownBy(() -> filter.filter(request(), returning(response)).block())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("errors on a real challenge interstitial")
    void rejectsChallengePage() {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .body("<html><body><div id=\"cf-browser-verification\"></div></body></html>")
                .build();

        assertThatThrownBy(() -> filter.filter(request(), returning(response)).block())
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("does not mistake data for a block marker")
    void allowsPageMentioningAwkwardNumbers() {
        // The same class of false positive that once made a stock split ratio
        // of 1.1428571429 read as an HTTP 429.
        String html = pageOf("<td>BIAT</td><td>1.1428571429</td><td>429</td>");
        ClientResponse response = ClientResponse.create(HttpStatus.OK).body(html).build();

        assertThat(filter.filter(request(), returning(response)).block()).isNotNull();
    }
}
