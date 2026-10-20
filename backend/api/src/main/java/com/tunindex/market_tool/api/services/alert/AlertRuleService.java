package com.tunindex.market_tool.api.services.alert;

import com.tunindex.market_tool.api.dto.alert.AlertRuleDto;
import com.tunindex.market_tool.api.dto.alert.CreateAlertRuleRequest;
import com.tunindex.market_tool.api.entities.AlertRule;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.enums.AlertType;
import com.tunindex.market_tool.api.repository.AlertRuleRepository;
import com.tunindex.market_tool.api.repository.UserRepository;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    /** A ceiling that keeps one account from turning the evaluator into a crawler. */
    private static final long MAX_ENABLED_RULES_PER_USER = 50;

    private final AlertRuleRepository alertRuleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AlertRuleDto> list(Authentication authentication) {
        User user = resolveUser(authentication);
        return alertRuleRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(AlertRuleDto::fromEntity)
                .toList();
    }

    @Transactional
    public AlertRuleDto create(Authentication authentication, CreateAlertRuleRequest request) {
        User user = resolveUser(authentication);

        AlertType type;
        try {
            type = AlertType.valueOf(request.getType().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            List<String> valid = Arrays.stream(AlertType.values()).map(Enum::name).toList();
            throw new InvalidEntityException("Invalid alert type", ErrorCodes.INVALID_PARAMETER,
                    List.of("'" + request.getType() + "' is not a valid alert type. Valid: " + String.join(", ", valid)));
        }

        if (type.isRequiresThreshold() && request.getThreshold() == null) {
            throw new InvalidEntityException("Threshold is required for this alert type",
                    ErrorCodes.INVALID_PARAMETER,
                    List.of(type.name() + " needs a threshold value, e.g. a price or a score."));
        }

        if (alertRuleRepository.countByUserIdAndEnabledTrue(user.getId()) >= MAX_ENABLED_RULES_PER_USER) {
            throw new InvalidEntityException("Too many active alerts", ErrorCodes.INVALID_PARAMETER,
                    List.of("You can have at most " + MAX_ENABLED_RULES_PER_USER
                            + " active alerts. Disable or delete one first."));
        }

        AlertRule saved = alertRuleRepository.save(AlertRule.builder()
                .user(user)
                .symbol(request.getSymbol().trim().toUpperCase())
                // Event types carry no threshold even if one was posted.
                .threshold(type.isRequiresThreshold() ? request.getThreshold() : null)
                .type(type)
                .enabled(true)
                .build());

        log.info("🔔 Alert rule created: {} {} for user {}", type, saved.getSymbol(), user.getId());
        return AlertRuleDto.fromEntity(saved);
    }

    @Transactional
    public AlertRuleDto toggle(Authentication authentication, Long id) {
        User user = resolveUser(authentication);
        AlertRule rule = alertRuleRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new InvalidEntityException("Alert rule not found",
                        ErrorCodes.INVALID_PARAMETER, List.of("id: " + id)));
        rule.setEnabled(!rule.isEnabled());
        // Re-enabling starts from a clean slate so an old reading can't make
        // the next evaluation look like a fresh crossing.
        if (rule.isEnabled()) {
            rule.setLastObservedValue(null);
        }
        return AlertRuleDto.fromEntity(alertRuleRepository.save(rule));
    }

    @Transactional
    public void delete(Authentication authentication, Long id) {
        User user = resolveUser(authentication);
        AlertRule rule = alertRuleRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new InvalidEntityException("Alert rule not found",
                        ErrorCodes.INVALID_PARAMETER, List.of("id: " + id)));
        alertRuleRepository.delete(rule);
    }

    /** The catalogue the UI builds its "new alert" form from. */
    public List<AlertTypeDto> availableTypes() {
        return Arrays.stream(AlertType.values())
                .map(type -> new AlertTypeDto(type.name(), type.getDescription(), type.isRequiresThreshold()))
                .toList();
    }

    public record AlertTypeDto(String type, String description, boolean requiresThreshold) {}

    private User resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidEntityException("Not authenticated", ErrorCodes.USER_NOT_AUTHENTICATED, Collections.emptyList());
        }
        String email = authentication.getName();
        return userRepository.findUserByEmail(email)
                .orElseThrow(() -> new InvalidEntityException("User not found", ErrorCodes.USER_NOT_FOUND,
                        Collections.singletonList("email: " + email)));
    }
}
