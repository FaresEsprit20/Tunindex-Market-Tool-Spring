package com.tunindex.market_tool.core.scheduler;

public interface DataScheduler {

    /**
     * Start the scheduler with default interval (30 minutes)
     */
    void start();

    /**
     * Start the scheduler with custom interval
     * @param intervalMinutes interval in minutes between pipeline runs
     */
    void start(int intervalMinutes);

    /**
     * Stop the scheduler
     */
    void stop();

    /**
     * Check if scheduler is running
     */
    boolean isRunning();

    /**
     * Run pipeline once immediately
     */
    void runOnce();
}