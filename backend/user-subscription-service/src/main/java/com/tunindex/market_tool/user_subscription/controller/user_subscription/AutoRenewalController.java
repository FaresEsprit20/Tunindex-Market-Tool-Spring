package com.tunindex.market_tool.user_subscription.controller.user_subscription;

import com.tunindex.market_tool.user_subscription.entities.UserSubscription;
import com.tunindex.market_tool.user_subscription.repository.UserSubscriptionRepository;
import com.tunindex.market_tool.user_subscription.service.user_subscription.AutoRenewalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AutoRenewalController implements AutoRenewalApi {

    private final AutoRenewalService autoRenewalService;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Override
    public ResponseEntity<Map<String, Object>> toggleAutoRenewal(Long subscriptionId, boolean enabled) {
        log.info("PUT /api/auto-renewal/{}/toggle - enabled: {}", subscriptionId, enabled);

        boolean success = autoRenewalService.toggleAutoRenewal(subscriptionId, enabled);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("subscriptionId", subscriptionId);
        response.put("autoRenewEnabled", enabled);
        response.put("message", success ? "Auto-renewal setting updated successfully" : "Failed to update auto-renewal setting");

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> manualRenewal(Long subscriptionId) {
        log.info("POST /api/auto-renewal/{}/renew", subscriptionId);

        boolean success = autoRenewalService.manualRenewal(subscriptionId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("subscriptionId", subscriptionId);
        response.put("message", success ? "Subscription renewed successfully" : "Failed to renew subscription");

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> getAutoRenewalStatus(Long subscriptionId) {
        log.info("GET /api/auto-renewal/{}/status", subscriptionId);

        UserSubscription subscription = userSubscriptionRepository.findById(subscriptionId).orElse(null);

        Map<String, Object> response = new HashMap<>();

        if (subscription == null) {
            response.put("exists", false);
            response.put("message", "Subscription not found");
            return ResponseEntity.ok(response);
        }

        response.put("exists", true);
        response.put("subscriptionId", subscriptionId);
        response.put("autoRenewEnabled", subscription.getAutoRenew());
        response.put("status", subscription.getStatus().name());
        response.put("endDate", subscription.getEndDate());
        response.put("renewalFailed", subscription.getRenewalFailed());
        response.put("renewalAttempts", subscription.getRenewalAttempts());
        response.put("message", "Auto-renewal status retrieved successfully");

        return ResponseEntity.ok(response);
    }


}