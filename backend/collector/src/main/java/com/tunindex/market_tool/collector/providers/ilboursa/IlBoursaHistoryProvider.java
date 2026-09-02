package com.tunindex.market_tool.collector.providers.ilboursa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ilboursa.com's per-symbol "Télécharger les cotations" form is a genuine
 * daily OHLCV export (real historical trading data — see the form's own
 * description: "le cours d'ouverture, le plus haut, le plus bas, le cours de
 * clôture et le volume d'actions échangées"), not a chart image. It's an
 * ASP.NET Core antiforgery-protected form: a GET returns both a hidden
 * __RequestVerificationToken field AND a matching .AspNetCore.Antiforgery
 * cookie, and the POST is only accepted when both are presented together —
 * so this is a real two-step flow, not optional cookie handling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IlBoursaHistoryProvider {

    private static final String BASE_URL = "https://www.ilboursa.com/marches/download/";
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("__RequestVerificationToken\"\\s*type=\"hidden\"\\s*value=\"([^\"]+)\"");
    private static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final WebClient webClient;

    public record PricePoint(LocalDate tradeDate, BigDecimal open, BigDecimal high, BigDecimal low,
                              BigDecimal close, Long volume) {
    }

    public Mono<List<PricePoint>> fetchHistory(String symbol, LocalDate from, LocalDate to) {
        String url = BASE_URL + symbol;

        return webClient.get()
                .uri(url)
                // The shared WebClient bean advertises "gzip, deflate, br" by
                // default; Reactor Netty's client doesn't decode Brotli, so a
                // br-compressed response silently comes through garbled/short
                // instead of as real HTML. identity sidesteps it entirely —
                // same fix StockAnalysisProvider already needed for the
                // other scraper.
                .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                .retrieve()
                .toEntity(String.class)
                .flatMap(getResponse -> {
                    String token = extractToken(getResponse.getBody());
                    String cookie = extractAntiforgeryCookie(getResponse.getHeaders());
                    log.debug("ilboursa GET for {}: status={}, bodyLen={}, tokenFound={}, cookieFound={}",
                            symbol, getResponse.getStatusCode(),
                            getResponse.getBody() != null ? getResponse.getBody().length() : -1,
                            token != null, cookie != null);

                    if (token == null || cookie == null) {
                        return Mono.error(new IllegalStateException(
                                "Could not obtain antiforgery token/cookie for " + symbol));
                    }

                    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                    form.add("dtFrom", from.toString());
                    form.add("dtTo", to.toString());
                    form.add("__RequestVerificationToken", token);

                    return webClient.post()
                            .uri(url)
                            .header(HttpHeaders.COOKIE, cookie)
                            .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(BodyInserters.fromFormData(form))
                            .retrieve()
                            .bodyToMono(String.class);
                })
                .map(csv -> parseCsv(symbol, csv))
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .doOnError(e -> {
                    Throwable root = e.getCause() != null ? e.getCause() : e;
                    log.warn("⚠️ ilboursa history fetch failed for {}: {} ({})", symbol, root.getMessage(), root.getClass().getSimpleName());
                })
                .onErrorReturn(List.of());
    }

    private String extractToken(String html) {
        if (html == null) return null;
        Matcher m = TOKEN_PATTERN.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    /**
     * The antiforgery cookie name is per-app-instance random
     * (".AspNetCore.Antiforgery.XXXX"), so match by prefix rather than a
     * fixed name.
     */
    private String extractAntiforgeryCookie(HttpHeaders headers) {
        List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
        if (setCookies == null) return null;
        for (String setCookie : setCookies) {
            if (setCookie.startsWith(".AspNetCore.Antiforgery")) {
                return setCookie.split(";", 2)[0];
            }
        }
        return null;
    }

    private List<PricePoint> parseCsv(String symbol, String csv) {
        List<PricePoint> points = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return points;
        }

        String[] lines = csv.split("\\R");
        // Header: symbole;date;ouverture;haut;bas;cloture;volume
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] cols = line.split(";");
            if (cols.length < 7) continue;

            try {
                LocalDate tradeDate = LocalDate.parse(cols[1], CSV_DATE_FORMAT);
                points.add(new PricePoint(
                        tradeDate,
                        parseFrenchDecimal(cols[2]),
                        parseFrenchDecimal(cols[3]),
                        parseFrenchDecimal(cols[4]),
                        parseFrenchDecimal(cols[5]),
                        parseFrenchLong(cols[6])
                ));
            } catch (Exception e) {
                log.debug("Skipping unparseable history row for {}: {}", symbol, line);
            }
        }
        return points;
    }

    private BigDecimal parseFrenchDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return new BigDecimal(raw.trim().replace(",", "."));
    }

    private Long parseFrenchLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Long.parseLong(raw.trim().replace(" ", "").replace("\u00A0", ""));
    }
}
