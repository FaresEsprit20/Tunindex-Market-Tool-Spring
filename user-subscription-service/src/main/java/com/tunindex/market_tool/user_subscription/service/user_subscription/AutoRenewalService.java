package com.tunindex.market_tool.user_subscription.service.user_subscription;

public interface AutoRenewalService {

    void processAutoRenewals();

    boolean manualRenewal(Long subscriptionId);

    boolean toggleAutoRenewal(Long subscriptionId, boolean enabled);
}