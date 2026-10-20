package com.tunindex.market_tool.collector.services.backfill;

import java.util.Map;

public interface BackfillService {

    /**
     * Walks every tracked symbol and populates the two datasets that are
     * otherwise only fetched when a user happens to open that stock: daily
     * price history and news headlines. Runs in the background; returns
     * immediately with the run's starting state.
     *
     * @return false when a backfill is already in progress.
     */
    boolean start(int historyDays, boolean includeNews);

    /** Progress of the current or most recent run. */
    Map<String, Object> status();
}
