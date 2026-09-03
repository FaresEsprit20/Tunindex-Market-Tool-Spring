package com.tunindex.market_tool.api.services.alert;

import com.tunindex.market_tool.api.dto.scoring.OpportunityScoreResponseDto;
import com.tunindex.market_tool.api.dto.stock.StockResponseDto;
import com.tunindex.market_tool.api.entities.AlertRule;
import com.tunindex.market_tool.api.repository.AlertRuleRepository;
import com.tunindex.market_tool.api.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates every enabled alert rule against real, freshly-read figures and
 * publishes a notification when one trips.
 *
 * <p>Two properties matter for it not to be annoying:
 *
 * <ul>
 *   <li><b>Fires on the crossing, not the state.</b> A rule stores the value
 *       it last saw; "price above 100" notifies when the price moves from
 *       below to above, not on every pass while it sits at 105.</li>
 *   <li><b>Reads the same numbers the UI shows.</b> Prices come from the
 *       stock endpoint and scores from the Tunindex Scorer, so a user can
 *       always open the stock and see what the alert saw.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private static final String COLLECTOR_STOCK_URL = "http://collector-service/internal/stock-data";
    private static final String COLLECTOR_SCORING_URL = "http://collector-service/internal/scoring";

    private final AlertRuleRepository alertRuleRepository;
    private final NotificationService notificationService;
    private final WebClient.Builder webClientBuilder;

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    /**
     * Runs a few minutes apart: often enough that an alert is useful during
     * a session, rare enough that it doesn't re-scrape the source or spam
     * the collector. Scores are only fetched for rules that need them.
     */
    @Scheduled(fixedDelayString = "${market-tool.alerts.evaluation-interval-ms:300000}",
            initialDelayString = "${market-tool.alerts.initial-delay-ms:60000}")
    public void evaluateAll() {
        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();
        if (rules.isEmpty()) {
            return;
        }

        log.debug("🔔 Evaluating {} enabled alert rules", rules.size());

        // One lookup per distinct symbol, not per rule — several rules on the
        // same stock are the normal case.
        Map<String, StockResponseDto> stockCache = new HashMap<>();
        Map<String, OpportunityScoreResponseDto> scoreCache = new HashMap<>();

        int fired = 0;
        for (AlertRule rule : rules) {
            try {
                if (evaluate(rule, stockCache, scoreCache)) {
                    fired++;
                }
            } catch (Exception e) {
                log.warn("Alert rule {} ({} {}) failed to evaluate: {}",
                        rule.getId(), rule.getType(), rule.getSymbol(), e.getMessage());
            }
        }

        if (fired > 0) {
            log.info("🔔 {} alert(s) fired across {} rules", fired, rules.size());
        }
    }

    @Transactional
    protected boolean evaluate(AlertRule rule,
                               Map<String, StockResponseDto> stockCache,
                               Map<String, OpportunityScoreResponseDto> scoreCache) {

        String symbol = rule.getSymbol();

        return switch (rule.getType()) {
            case PRICE_ABOVE -> {
                StockResponseDto stock = stock(symbol, stockCache);
                yield crossedUp(rule, stock == null ? null : stock.getLastPrice(),
                        String.format("%s is above %s", symbol, plain(rule.getThreshold())),
                        stock == null ? "" : String.format("Trading at %s %s.", plain(stock.getLastPrice()), safe(stock.getCurrency())),
                        "POSITIVE");
            }
            case PRICE_BELOW -> {
                StockResponseDto stock = stock(symbol, stockCache);
                yield crossedDown(rule, stock == null ? null : stock.getLastPrice(),
                        String.format("%s is below %s", symbol, plain(rule.getThreshold())),
                        stock == null ? "" : String.format("Trading at %s %s.", plain(stock.getLastPrice()), safe(stock.getCurrency())),
                        "NEGATIVE");
            }
            case DAY_MOVE_EXCEEDS -> {
                StockResponseDto stock = stock(symbol, stockCache);
                BigDecimal move = dayMovePct(stock);
                if (move == null) yield false;
                BigDecimal magnitude = move.abs();
                yield crossedUp(rule, magnitude,
                        String.format("%s moved %s%% today", symbol, plain(move)),
                        "Day move passed your threshold of " + plain(rule.getThreshold()) + "%.",
                        move.signum() >= 0 ? "POSITIVE" : "NEGATIVE");
            }
            case SCORE_ABOVE -> {
                OpportunityScoreResponseDto score = score(symbol, scoreCache);
                yield crossedUp(rule, score == null ? null : BigDecimal.valueOf(score.getOverallScore()),
                        String.format("%s scores above %s", symbol, plain(rule.getThreshold())),
                        score == null ? "" : String.format("Tunindex Score %d — %s.", score.getOverallScore(), verdictText(score)),
                        "POSITIVE");
            }
            case SCORE_BELOW -> {
                OpportunityScoreResponseDto score = score(symbol, scoreCache);
                yield crossedDown(rule, score == null ? null : BigDecimal.valueOf(score.getOverallScore()),
                        String.format("%s scores below %s", symbol, plain(rule.getThreshold())),
                        score == null ? "" : String.format("Tunindex Score %d — %s.", score.getOverallScore(), verdictText(score)),
                        "NEGATIVE");
            }
            case NEAR_52W_LOW -> {
                StockResponseDto stock = stock(symbol, stockCache);
                yield crossedUp(rule, stock == null ? null : stock.getCloseTo52weekslowPct(),
                        String.format("%s is near its 52-week low", symbol),
                        "Position in its 52-week range passed your threshold — often an entry point worth a look.",
                        "NEUTRAL");
            }
            case VERDICT_CHANGE -> {
                OpportunityScoreResponseDto score = score(symbol, scoreCache);
                if (score == null || score.getVerdict() == null) yield false;
                BigDecimal encoded = BigDecimal.valueOf(verdictRank(score.getVerdict()));
                BigDecimal previous = rule.getLastObservedValue();
                rule.setLastObservedValue(encoded);
                if (previous == null || previous.compareTo(encoded) == 0) {
                    alertRuleRepository.save(rule);
                    yield false;
                }
                boolean improved = encoded.compareTo(previous) > 0;
                fire(rule,
                        String.format("%s is now %s", symbol, verdictText(score)),
                        String.format("Its verdict %s. Tunindex Score %d.",
                                improved ? "improved" : "weakened", score.getOverallScore()),
                        improved ? "POSITIVE" : "NEGATIVE");
                yield true;
            }
            case NEGATIVE_NEWS -> evaluateNegativeNews(rule);
        };
    }

    /**
     * Fires when a NEGATIVE headline exists that is newer than the last time
     * this rule fired — so each story notifies once, not on every pass.
     */
    private boolean evaluateNegativeNews(AlertRule rule) {
        List<Map<String, Object>> news = webClientBuilder.build()
                .get()
                .uri("http://collector-service/internal/news/{symbol}?limit=5", rule.getSymbol())
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .timeout(Duration.ofSeconds(20))
                .onErrorReturn(List.of())
                .block();

        if (news == null || news.isEmpty()) {
            return false;
        }

        // The per-stock news endpoint carries no sentiment, so the impact
        // endpoint is the one that classifies. Read that instead.
        List<Map<String, Object>> impact = webClientBuilder.build()
                .get()
                .uri("http://collector-service/internal/news/{symbol}/impact?limit=5", rule.getSymbol())
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .timeout(Duration.ofSeconds(25))
                .onErrorReturn(List.of())
                .block();

        if (impact == null || impact.isEmpty()) {
            return false;
        }

        for (Map<String, Object> item : impact) {
            if (!"NEGATIVE".equals(item.get("sentiment"))) {
                continue;
            }
            Object publishedRaw = item.get("publishedAt");
            if (publishedRaw == null) {
                continue;
            }
            java.time.LocalDateTime publishedAt;
            try {
                publishedAt = java.time.LocalDateTime.parse(publishedRaw.toString());
            } catch (Exception e) {
                continue;
            }
            if (rule.getLastTriggeredAt() != null && !publishedAt.isAfter(rule.getLastTriggeredAt())) {
                continue;
            }
            fire(rule,
                    String.format("Negative headline on %s", rule.getSymbol()),
                    String.valueOf(item.get("headline")),
                    "NEGATIVE");
            return true;
        }
        return false;
    }

    // ── crossing helpers ───────────────────────────────────────────────────

    private boolean crossedUp(AlertRule rule, BigDecimal current, String title, String body, String tone) {
        if (current == null || rule.getThreshold() == null) {
            return false;
        }
        BigDecimal previous = rule.getLastObservedValue();
        rule.setLastObservedValue(current);

        boolean isAbove = current.compareTo(rule.getThreshold()) > 0;
        boolean wasAbove = previous != null && previous.compareTo(rule.getThreshold()) > 0;

        if (isAbove && !wasAbove) {
            fire(rule, title, body, tone);
            return true;
        }
        alertRuleRepository.save(rule);
        return false;
    }

    private boolean crossedDown(AlertRule rule, BigDecimal current, String title, String body, String tone) {
        if (current == null || rule.getThreshold() == null) {
            return false;
        }
        BigDecimal previous = rule.getLastObservedValue();
        rule.setLastObservedValue(current);

        boolean isBelow = current.compareTo(rule.getThreshold()) < 0;
        boolean wasBelow = previous != null && previous.compareTo(rule.getThreshold()) < 0;

        if (isBelow && !wasBelow) {
            fire(rule, title, body, tone);
            return true;
        }
        alertRuleRepository.save(rule);
        return false;
    }

    private void fire(AlertRule rule, String title, String body, String tone) {
        rule.setLastTriggeredAt(java.time.LocalDateTime.now());
        alertRuleRepository.save(rule);
        notificationService.publish(rule.getUser(), title, body, "ALERT", tone, rule.getSymbol());
        log.info("🔔 Alert fired: {} on {} for user {}", rule.getType(), rule.getSymbol(), rule.getUser().getId());
    }

    // ── data access ────────────────────────────────────────────────────────

    private StockResponseDto stock(String symbol, Map<String, StockResponseDto> cache) {
        return cache.computeIfAbsent(symbol, s -> webClientBuilder.build()
                .get()
                .uri(COLLECTOR_STOCK_URL + "/symbol/{symbol}", s)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(StockResponseDto.class)
                .timeout(Duration.ofSeconds(20))
                .onErrorResume(e -> {
                    log.warn("Alert evaluation: stock lookup failed for {}: {}", s, e.getMessage());
                    return reactor.core.publisher.Mono.empty();
                })
                .block());
    }

    private OpportunityScoreResponseDto score(String symbol, Map<String, OpportunityScoreResponseDto> cache) {
        return cache.computeIfAbsent(symbol, s -> webClientBuilder.build()
                .get()
                .uri(COLLECTOR_SCORING_URL + "/score/{symbol}", s)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(OpportunityScoreResponseDto.class)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(e -> {
                    log.warn("Alert evaluation: score lookup failed for {}: {}", s, e.getMessage());
                    return reactor.core.publisher.Mono.empty();
                })
                .block());
    }

    private BigDecimal dayMovePct(StockResponseDto stock) {
        if (stock == null || stock.getLastPrice() == null || stock.getPrevClose() == null
                || stock.getPrevClose().signum() == 0) {
            return null;
        }
        return stock.getLastPrice()
                .subtract(stock.getPrevClose())
                .divide(stock.getPrevClose(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Ordered so a numeric comparison tells improvement from deterioration. */
    private int verdictRank(String verdict) {
        return switch (verdict) {
            case "STRONG_BUY" -> 5;
            case "BUY" -> 4;
            case "WATCH" -> 3;
            case "HOLD" -> 2;
            default -> 1;
        };
    }

    private String verdictText(OpportunityScoreResponseDto score) {
        return switch (score.getVerdict()) {
            case "STRONG_BUY" -> "a strong buy";
            case "BUY" -> "a buy";
            case "WATCH" -> "one to watch";
            case "HOLD" -> "a hold";
            default -> "one to avoid";
        };
    }

    private String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
