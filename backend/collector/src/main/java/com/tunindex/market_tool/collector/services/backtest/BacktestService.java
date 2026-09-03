package com.tunindex.market_tool.collector.services.backtest;

import com.tunindex.market_tool.collector.dto.backtest.BacktestResultDto;

public interface BacktestService {

    /**
     * Replays the timing score over stored price history and reports what
     * followed each score band.
     *
     * @param horizonDays calendar days ahead to measure the forward return
     * @param stepDays    trading days between evaluation points per symbol,
     *                    kept wide enough that consecutive observations
     *                    aren't measuring largely the same window
     */
    BacktestResultDto run(int horizonDays, int stepDays);
}
