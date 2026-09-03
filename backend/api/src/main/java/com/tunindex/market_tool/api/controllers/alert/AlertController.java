package com.tunindex.market_tool.api.controllers.alert;

import com.tunindex.market_tool.api.dto.alert.AlertRuleDto;
import com.tunindex.market_tool.api.dto.alert.CreateAlertRuleRequest;
import com.tunindex.market_tool.api.dto.notification.NotificationDto;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.repository.UserRepository;
import com.tunindex.market_tool.api.services.alert.AlertRuleService;
import com.tunindex.market_tool.api.services.notification.NotificationService;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Standing alert rules and the notifications they produce")
public class AlertController {

    private final AlertRuleService alertRuleService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    // ── rules ──────────────────────────────────────────────────────────────

    @GetMapping(value = APP_ROOT + "/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the signed-in user's alert rules")
    public List<AlertRuleDto> list(Authentication authentication) {
        return alertRuleService.list(authentication);
    }

    @GetMapping(value = APP_ROOT + "/alerts/types", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "The alert types available, and which need a threshold")
    public List<AlertRuleService.AlertTypeDto> types() {
        return alertRuleService.availableTypes();
    }

    @PostMapping(value = APP_ROOT + "/alerts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an alert rule")
    public AlertRuleDto create(Authentication authentication, @Valid @RequestBody CreateAlertRuleRequest request) {
        return alertRuleService.create(authentication, request);
    }

    @PutMapping(value = APP_ROOT + "/alerts/{id}/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Enable or disable an alert rule")
    public AlertRuleDto toggle(Authentication authentication, @PathVariable Long id) {
        return alertRuleService.toggle(authentication, id);
    }

    @DeleteMapping(APP_ROOT + "/alerts/{id}")
    @Operation(summary = "Delete an alert rule")
    public void delete(Authentication authentication, @PathVariable Long id) {
        alertRuleService.delete(authentication, id);
    }

    // ── notifications ──────────────────────────────────────────────────────

    @GetMapping(value = APP_ROOT + "/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recent notifications for the signed-in user")
    public List<NotificationDto> notifications(Authentication authentication,
                                               @RequestParam(defaultValue = "30") int limit) {
        return notificationService.list(resolveUser(authentication).getId(), Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping(value = APP_ROOT + "/notifications/unread-count", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "How many notifications are unread")
    public Map<String, Long> unreadCount(Authentication authentication) {
        return Map.of("count", notificationService.unreadCount(resolveUser(authentication).getId()));
    }

    @PutMapping(value = APP_ROOT + "/notifications/{id}/read", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mark one notification read")
    public void markRead(Authentication authentication, @PathVariable Long id) {
        notificationService.markRead(resolveUser(authentication).getId(), id);
    }

    @PutMapping(value = APP_ROOT + "/notifications/read-all", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Mark every notification read")
    public Map<String, Integer> markAllRead(Authentication authentication) {
        return Map.of("updated", notificationService.markAllRead(resolveUser(authentication).getId()));
    }

    /**
     * Live notification stream. Held open by the server and delivered to as
     * events arrive, so the bell updates without the client polling.
     */
    @GetMapping(value = APP_ROOT + "/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Server-sent stream of this user's notifications")
    public SseEmitter stream(Authentication authentication) {
        return notificationService.stream(resolveUser(authentication).getId());
    }

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
