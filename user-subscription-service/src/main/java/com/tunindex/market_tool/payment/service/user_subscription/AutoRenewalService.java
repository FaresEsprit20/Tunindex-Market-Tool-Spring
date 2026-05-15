package com.tunindex.market_tool.payment.service.user_subscription;

public interface AutoRenewalService {

    void processAutoRenewals();

    boolean manualRenewal(Long subscriptionId);

    boolean toggleAutoRenewal(Long subscriptionId, boolean enabled);
}