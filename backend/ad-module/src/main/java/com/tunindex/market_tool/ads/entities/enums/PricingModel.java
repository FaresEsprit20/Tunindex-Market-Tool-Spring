package com.tunindex.market_tool.ads.entities.enums;

import lombok.Getter;

/**
 * What the advertiser actually pays for.
 *
 * <p>This is the field that turns activity into money, and each model bills a
 * different event - so the same thousand page views earn nothing on CPC and a
 * full rate on CPM. Recording the model alongside the rate is what lets
 * revenue be derived rather than guessed.
 *
 * <p>{@code billableEvent} names the event that counts, so the revenue
 * calculation has one place to look instead of a switch in every caller.
 */
@Getter
public enum PricingModel {

    /** Paid per thousand impressions, the standard display arrangement. */
    CPM("Cost per mille", BillableEvent.IMPRESSION, 1000),

    /** Paid per click through to the advertiser. */
    CPC("Cost per click", BillableEvent.CLICK, 1),

    /**
     * Paid per completed view.
     *
     * <p>"Completed" is the operative word: a video abandoned early earns
     * nothing, which is why the impression record tracks completion rather
     * than just whether the ad started.
     */
    CPV("Cost per view", BillableEvent.COMPLETED_VIEW, 1),

    /** Paid per conversion - a signup or purchase at the advertiser's end. */
    CPA("Cost per action", BillableEvent.CONVERSION, 1),

    /**
     * A fixed fee for a period, regardless of traffic.
     *
     * <p>Earns nothing per event, so it is deliberately excluded from
     * event-driven revenue; counting it there would double-bill the sponsor.
     */
    FLAT_RATE("Flat rate", BillableEvent.NONE, 1),

    /** Revenue decided and reported by the network, not computed here. */
    NETWORK_REPORTED("Network reported", BillableEvent.NONE, 1);

    /** Which event, if any, moves money. */
    public enum BillableEvent { IMPRESSION, CLICK, COMPLETED_VIEW, CONVERSION, NONE }

    private final String displayName;
    private final BillableEvent billableEvent;

    /** How many events one unit of the rate covers - 1000 for CPM, else 1. */
    private final int eventsPerUnit;

    PricingModel(String displayName, BillableEvent billableEvent, int eventsPerUnit) {
        this.displayName = displayName;
        this.billableEvent = billableEvent;
        this.eventsPerUnit = eventsPerUnit;
    }

    public boolean isEventDriven() {
        return billableEvent != BillableEvent.NONE;
    }
}
