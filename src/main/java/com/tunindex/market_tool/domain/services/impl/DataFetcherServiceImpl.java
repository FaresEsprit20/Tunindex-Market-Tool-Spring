package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.CaptchaException;
import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.core.webscraping.*;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.services.fetcher.DataFetcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataFetcherServiceImpl implements DataFetcherService {

    private final WebClient webClient;
    private final UserAgentManager userAgentManager;
    private final ProxyManager proxyManager;
    private final RateLimiterManager rateLimiterManager;
    private final RetryManager retryManager;
    private final CaptchaDetector captchaDetector;
    private final BrowserFingerprintGenerator fingerprintGenerator;

    @Override
    public Mono<RawStockData> fetchStockData(String symbol) {
        Constants.StockInfo stockInfo = Constants.TUNISIAN_STOCKS.get(symbol);
        if (stockInfo == null) {
            return Mono.error(new DataFetchException(
                    ErrorCodes.PROVIDER_NOT_FOUND,
                    Constants.PROVIDER_INVESTINGCOM,
                    symbol,
                    "Stock not found in configuration",
                    Collections.singletonList("Symbol: " + symbol)
            ));
        }

        RawStockData rawData = new RawStockData();
        rawData.setSymbol(symbol);
        rawData.setStockInfo(stockInfo);

        String mainUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();
        String balanceUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_BALANCE_SHEET;
        String incomeUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_INCOME_STATEMENT;
        String financialUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_FINANCIAL_SUMMARY;

        boolean useProxy = Constants.USE_PROXY;

        return fetchAndSetWithStealth(mainUrl, useProxy, rawData::setMainPageHtml, symbol)
                .then(fetchAndSetWithStealth(balanceUrl, useProxy, rawData::setBalanceSheetHtml, symbol))
                .then(fetchAndSetWithStealth(incomeUrl, useProxy, rawData::setIncomeStatementHtml, symbol))
                .then(fetchAndSetWithStealth(financialUrl, useProxy, rawData::setFinancialSummaryHtml, symbol))
                .thenReturn(rawData)
                .onErrorMap(e -> new DataFetchException(
                        ErrorCodes.DATA_FETCH_FAILED,
                        Constants.PROVIDER_INVESTINGCOM,
                        symbol,
                        "Failed to fetch stock data: " + e.getMessage(),
                        Collections.singletonList(e.getMessage())
                ));
    }

    @Override
    public Mono<String> fetchUrl(String url, boolean useProxy) {
        return fetchUrlWithStealth(url, useProxy, null);
    }

    @Override
    public Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs) {
        log.debug("Fetching URL: {} (useProxy: {}, retries: {})", url, useProxy, retries);

        return rateLimiterManager.waitForSlot()
                .then(Mono.defer(() -> buildStealthRequest(url, useProxy)))
                .flatMap(response -> checkForCaptcha(response, url))
                .retryWhen(Retry.backoff(retries, Duration.ofMillis(backoffMs))
                        .maxBackoff(Duration.ofMillis(backoffMs * 4))
                        .filter(throwable -> !(throwable instanceof CaptchaException))
                        .doBeforeRetry(retrySignal -> {
                            long currentDelay = (long) (backoffMs * Math.pow(2, retrySignal.totalRetries()));
                            log.warn("Retry {} for URL: {} after {}ms",
                                    retrySignal.totalRetries() + 1,
                                    url,
                                    currentDelay);
                        }));
    }

    public Mono<String> fetchUrlWithStealth(String url, boolean useProxy, String symbol) {
        log.debug("Fetching URL with stealth: {}", url);

        return rateLimiterManager.waitForSlot()
                .then(Mono.defer(() -> buildStealthRequest(url, useProxy)))
                .flatMap(response -> checkForCaptcha(response, url))
                .retryWhen(Retry.backoff(3, Duration.ofMillis(1000))
                        .maxBackoff(Duration.ofMillis(10000))
                        .filter(throwable -> !(throwable instanceof CaptchaException))
                        .doBeforeRetry(retrySignal -> {
                            log.warn("Retry attempt {} for URL: {}",
                                    retrySignal.totalRetries() + 1, url);
                        }));
    }

    /**
     * Check response for CAPTCHA or block
     */
    private Mono<String> checkForCaptcha(String response, String url) {
        if (captchaDetector.hasCaptcha(response)) {
            String captchaType = captchaDetector.getCaptchaType(response);
            log.error("CAPTCHA detected for {}: {}", url, captchaType);
            return Mono.error(new CaptchaException(
                    ErrorCodes.CAPTCHA_DETECTED,
                    Constants.PROVIDER_INVESTINGCOM,
                    captchaType,
                    "CAPTCHA detected when fetching URL",
                    Collections.singletonList(url)
            ));
        } else if (captchaDetector.isBlocked(response)) {
            log.error("Request blocked for URL: {}", url);
            return Mono.error(new CaptchaException(
                    ErrorCodes.BLOCKED_BY_PROVIDER,
                    Constants.PROVIDER_INVESTINGCOM,
                    "BLOCKED",
                    "Request blocked by provider",
                    Collections.singletonList(url)
            ));
        }
        return Mono.just(response);
    }

    private Mono<Void> fetchAndSetWithStealth(String url, boolean useProxy, Consumer<String> setter, String symbol) {
        return fetchUrlWithStealth(url, useProxy, symbol)
                .doOnNext(setter)
                .onErrorResume(e -> {
                    if (e instanceof CaptchaException) {
                        log.error("CAPTCHA/Blocked for URL: {} - {}", url, e.getMessage());
                    } else {
                        log.warn("Failed to fetch URL: {} - {}", url, e.getMessage());
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Mono<String> buildStealthRequest(String url, boolean useProxy) {
        WebClient.RequestHeadersSpec<?> request = webClient.get()
                .uri(url)
                .header("User-Agent", userAgentManager.getRandomUserAgent())
                .header("Accept", Constants.DEFAULT_ACCEPT)
                .header("Accept-Language", fingerprintGenerator.getAcceptLanguage())
                .header("Accept-Encoding", Constants.DEFAULT_ACCEPT_ENCODING)
                .header("Connection", Constants.DEFAULT_CONNECTION)
                .header("Upgrade-Insecure-Requests", "1")
                .header("Cache-Control", "max-age=0")
                .header("Sec-Ch-Ua", fingerprintGenerator.getSecChUa())
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", fingerprintGenerator.getPlatform())
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1");

        return request.retrieve()
                .bodyToMono(String.class)
                .handle((response, sink) -> {
                    if (response == null || response.isEmpty()) {
                        sink.error(new DataFetchException(
                                ErrorCodes.DATA_FETCH_EMPTY_RESPONSE,
                                Constants.PROVIDER_INVESTINGCOM,
                                null,
                                "Empty response from URL: " + url,
                                Collections.singletonList(url)
                        ));
                    } else {
                        sink.next(response);
                    }
                });
    }
}