package com.tunindex.market_tool.payment.service.user_subscription;

import com.tunindex.market_tool.payment.client.ApiServiceClient;
import com.tunindex.market_tool.payment.client.EmailServiceClient;
import com.tunindex.market_tool.payment.dto.UserPaymentInfoDto;
import com.tunindex.market_tool.payment.entities.SubscriptionPlan;
import com.tunindex.market_tool.payment.entities.UserSubscription;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;
import com.tunindex.market_tool.payment.repository.SubscriptionPlanRepository;
import com.tunindex.market_tool.payment.repository.UserSubscriptionRepository;
import com.tunindex.market_tool.payment.service.gateway.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class KonnectAutoRenewalServiceImpl implements AutoRenewalService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final ApiServiceClient apiServiceClient;
    private final EmailServiceClient emailServiceClient;

    private static final int RENEWAL_DAYS_BEFORE_EXPIRY = 1;
    private static final int MAX_RENEWAL_ATTEMPTS = 3;

    @Override
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void processAutoRenewals() {
        log.info("🔄 Starting auto-renewal processing");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now;
        LocalDateTime endDate = now.plusDays(RENEWAL_DAYS_BEFORE_EXPIRY);

        var subscriptionsToRenew = userSubscriptionRepository.findSubscriptionsToRenew(
                startDate, endDate, SubscriptionStatus.ACTIVE);

        log.info("Found {} subscriptions to renew", subscriptionsToRenew.size());

        for (UserSubscription subscription : subscriptionsToRenew) {
            try {
                processSingleRenewal(subscription);
            } catch (Exception e) {
                log.error("Failed to renew subscription {}: {}", subscription.getId(), e.getMessage());
                handleFailedRenewal(subscription, e.getMessage());
            }
        }
    }

    private void processSingleRenewal(UserSubscription subscription) {
        log.info("Processing renewal for subscription: {}", subscription.getId());

        SubscriptionPlan plan = subscriptionPlanRepository.findById(subscription.getPlan().getId()).orElse(null);
        if (plan == null) {
            throw new RuntimeException("Plan not found");
        }

        // Calculate amount
        double amount = plan.getPriceMonthly().doubleValue();
        if (subscription.getBillingPeriod() != null && "YEARLY".equals(subscription.getBillingPeriod().name())) {
            amount = plan.getPriceYearly().doubleValue();
        }

        // Get user info
        UserPaymentInfoDto user = apiServiceClient.getUserPaymentInfo(subscription.getUserId());
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        // Process payment (placeholder)
        boolean paymentSuccess = true; // Replace with actual payment call

        if (paymentSuccess) {
            // Extend subscription
            LocalDateTime newEndDate;
            if (subscription.getBillingPeriod() != null && "YEARLY".equals(subscription.getBillingPeriod().name())) {
                newEndDate = subscription.getEndDate().plusYears(1);
            } else {
                newEndDate = subscription.getEndDate().plusMonths(1);
            }

            subscription.setEndDate(newEndDate);
            subscription.setRenewalFailed(false);
            subscription.setRenewalAttempts(0);
            subscription.setLastRenewalError(null);
            userSubscriptionRepository.save(subscription);

            // Send receipt email using existing method
            emailServiceClient.sendPaymentReceiptEmail(
                    user.getEmail(),
                    user.getFirstName(),
                    String.valueOf(amount),
                    "TND",
                    "RENEWAL-" + subscription.getId(),
                    plan.getName()
            );

            log.info("✅ Subscription {} renewed successfully", subscription.getId());
        } else {
            throw new RuntimeException("Payment failed");
        }
    }

    private void handleFailedRenewal(UserSubscription subscription, String error) {
        int attempts = subscription.getRenewalAttempts() + 1;

        UserPaymentInfoDto user = apiServiceClient.getUserPaymentInfo(subscription.getUserId());

        if (attempts >= MAX_RENEWAL_ATTEMPTS) {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setRenewalFailed(true);
            subscription.setLastRenewalError(error);
            userSubscriptionRepository.save(subscription);

            // Send confirmation email (payment failed notification)
            if (user != null) {
                emailServiceClient.sendPaymentConfirmationEmail(
                        user.getEmail(),
                        user.getFirstName(),
                        String.valueOf(subscription.getPlan().getPriceMonthly()),
                        "TND",
                        "FAILED-" + subscription.getId()
                );
            }

            log.warn("Subscription {} expired after {} failed attempts", subscription.getId(), attempts);
        } else {
            subscription.setRenewalAttempts(attempts);
            subscription.setLastRenewalAttempt(LocalDateTime.now());
            subscription.setLastRenewalError(error);
            subscription.setRenewalFailed(true);
            userSubscriptionRepository.save(subscription);

            // Send payment failed notification
            if (user != null) {
                emailServiceClient.sendPaymentConfirmationEmail(
                        user.getEmail(),
                        user.getFirstName(),
                        String.valueOf(subscription.getPlan().getPriceMonthly()),
                        "TND",
                        "RETRY-" + subscription.getId()
                );
            }
        }
    }

    @Override
    @Transactional
    public boolean manualRenewal(Long subscriptionId) {
        log.info("Manual renewal requested for subscription: {}", subscriptionId);

        UserSubscription subscription = userSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            log.error("Subscription not found: {}", subscriptionId);
            return false;
        }

        try {
            processSingleRenewal(subscription);
            return true;
        } catch (Exception e) {
            log.error("Manual renewal failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional
    public boolean toggleAutoRenewal(Long subscriptionId, boolean enabled) {
        log.info("Toggling auto-renewal for subscription {} to {}", subscriptionId, enabled);

        int updated = userSubscriptionRepository.updateAutoRenewSetting(subscriptionId, enabled);
        return updated > 0;
    }
}