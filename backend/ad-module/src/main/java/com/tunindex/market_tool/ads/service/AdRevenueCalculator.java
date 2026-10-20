package com.tunindex.market_tool.ads.service;

import com.tunindex.market_tool.ads.entities.AdEvent;
import com.tunindex.market_tool.ads.entities.Advertisement;
import com.tunindex.market_tool.ads.entities.enums.PricingModel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Works out what a single event earned.
 *
 * <p>Isolated from the recording path so the arithmetic can be tested on its
 * own. It is the only place that knows how a pricing model turns activity into
 * money, which keeps a wrong answer to one question rather than several.
 *
 * <p>The rule is the same throughout: an event earns only if the ad's pricing
 * model bills that specific event. An impression under a cost-per-click deal
 * is worth nothing, and a skipped video under cost-per-view is worth nothing.
 * Both return zero rather than null - zero is a measurement, null would mean
 * we do not know, and the difference decides whether a total can be trusted.
 */
@Service
public class AdRevenueCalculator {

    /**
     * Six decimal places because per-event amounts are tiny.
     *
     * <p>A five-dinar CPM is one two-thousandth of a dinar per impression.
     * Rounded to the millime an event is worth, every impression would record
     * as zero and the campaign would appear to earn nothing at all.
     */
    private static final int REVENUE_SCALE = 6;

    /**
     * What this event earns, never null.
     *
     * @param ad    the ad, for its pricing model and rate
     * @param event what happened
     */
    public BigDecimal revenueFor(Advertisement ad, AdEvent.EventType event) {
        if (ad == null || event == null) {
            return BigDecimal.ZERO.setScale(REVENUE_SCALE);
        }
        // A house ad promotes us; counting it as income would inflate every
        // total with money nobody is paying.
        if (ad.getSource() == null || !ad.getSource().earnsRevenue()) {
            return BigDecimal.ZERO.setScale(REVENUE_SCALE);
        }

        PricingModel model = ad.getPricingModel();
        if (model == null || !model.isEventDriven()) {
            // Flat-rate and network-reported deals are settled elsewhere.
            // Accruing per event as well would bill the same money twice.
            return BigDecimal.ZERO.setScale(REVENUE_SCALE);
        }
        if (!billsThis(model, event)) {
            return BigDecimal.ZERO.setScale(REVENUE_SCALE);
        }

        BigDecimal rate = ad.getRate();
        if (rate == null || rate.signum() <= 0) {
            return BigDecimal.ZERO.setScale(REVENUE_SCALE);
        }

        // CPM quotes a price per thousand; everything else is per event.
        return rate.divide(BigDecimal.valueOf(model.getEventsPerUnit()),
                REVENUE_SCALE, RoundingMode.HALF_UP);
    }

    /** Whether this model pays for this particular event. */
    private boolean billsThis(PricingModel model, AdEvent.EventType event) {
        return switch (model.getBillableEvent()) {
            case IMPRESSION -> event == AdEvent.EventType.IMPRESSION;
            case CLICK -> event == AdEvent.EventType.CLICK;
            // Deliberately not IMPRESSION: a view that was started and
            // abandoned is exactly what cost-per-view does not pay for.
            case COMPLETED_VIEW -> event == AdEvent.EventType.COMPLETED_VIEW;
            case CONVERSION -> event == AdEvent.EventType.CONVERSION;
            case NONE -> false;
        };
    }
}
