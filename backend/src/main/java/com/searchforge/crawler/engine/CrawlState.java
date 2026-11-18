package com.searchforge.crawler.engine;

/**
 * Operational lifecycle states for the CrawlEngine.
 */
public enum CrawlState {
    /**
     * Crawler is idle and ready to accept a crawl command.
     */
    IDLE,

    /**
     * Crawler is actively polling URLs, fetching pages, and indexing content.
     */
    RUNNING,

    /**
     * Stop signal has been issued; waiting for current active page to finish cleanly.
     */
    STOPPING,

    /**
     * Crawl was stopped prematurely by user or operator signal.
     */
    STOPPED,

    /**
     * Crawl finished naturally because the frontier was exhausted or page limits were reached.
     */
    COMPLETED
}
