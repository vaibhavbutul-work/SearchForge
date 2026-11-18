package com.searchforge.crawler.engine;

import com.searchforge.crawler.config.CrawlerProperties;
import com.searchforge.crawler.fetcher.DocumentFetcher;
import com.searchforge.crawler.fetcher.FetchedDocument;
import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.parser.HtmlParser;
import com.searchforge.crawler.parser.ParsedDocument;
import com.searchforge.crawler.politeness.PolitenessRateLimiter;
import com.searchforge.crawler.politeness.RobotsTxtService;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.crawler.url.CrawlTask;
import com.searchforge.crawler.url.UrlFrontier;
import com.searchforge.crawler.url.UrlNormalizer;
import com.searchforge.crawler.url.VisitedFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core orchestration engine of the SearchForge Web Crawler.
 *
 * Coordinates the full end-to-end crawling lifecycle:
 * 1. Seeds candidate URLs into the BFS UrlFrontier.
 * 2. Continuously dequeues candidate tasks in BFS order.
 * 3. Checks Robots.txt compliance and enforces per-domain politeness delays.
 * 4. Downloads HTML safely via DocumentFetcher (handling timeouts and binary guards).
 * 5. Parses DOM, extracts clean text, and discovers outbound links.
 * 6. Deduplicates via SHA-256 content hashes and persists documents to PostgreSQL.
 * 7. Enqueues newly discovered hyperlinks into the frontier for subsequent BFS hops.
 * 8. Enforces max-depth, max-pages, and safe shutdown signals.
 */
@Service
public class CrawlEngine {

    private static final Logger log = LoggerFactory.getLogger(CrawlEngine.class);

    private final CrawlerProperties properties;
    private final UrlFrontier frontier;
    private final VisitedFilter visitedFilter;
    private final RobotsTxtService robotsTxtService;
    private final PolitenessRateLimiter rateLimiter;
    private final DocumentFetcher fetcher;
    private final HtmlParser htmlParser;
    private final UrlNormalizer urlNormalizer;
    private final CrawledDocumentRepository repository;

    private final AtomicReference<CrawlState> state = new AtomicReference<>(CrawlState.IDLE);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger pagesCrawled = new AtomicInteger(0);
    private final AtomicInteger pagesFailed = new AtomicInteger(0);
    private volatile Instant startedAt;

    public CrawlEngine(CrawlerProperties properties,
                       UrlFrontier frontier,
                       VisitedFilter visitedFilter,
                       RobotsTxtService robotsTxtService,
                       PolitenessRateLimiter rateLimiter,
                       DocumentFetcher fetcher,
                       HtmlParser htmlParser,
                       UrlNormalizer urlNormalizer,
                       CrawledDocumentRepository repository) {
        this.properties = properties;
        this.frontier = frontier;
        this.visitedFilter = visitedFilter;
        this.robotsTxtService = robotsTxtService;
        this.rateLimiter = rateLimiter;
        this.fetcher = fetcher;
        this.htmlParser = htmlParser;
        this.urlNormalizer = urlNormalizer;
        this.repository = repository;
    }

    /**
     * Executes the crawl loop synchronously using configured seed URLs.
     */
    public void runCrawl() {
        runCrawl(properties.seedUrls());
    }

    /**
     * Executes the crawl loop synchronously using custom seed URLs.
     *
     * @param seedUrls The entry-point URLs to begin crawling.
     */
    public void runCrawl(List<String> seedUrls) {
        if (!state.compareAndSet(CrawlState.IDLE, CrawlState.RUNNING) &&
                !state.compareAndSet(CrawlState.STOPPED, CrawlState.RUNNING) &&
                !state.compareAndSet(CrawlState.COMPLETED, CrawlState.RUNNING)) {
            log.warn("CrawlEngine is already RUNNING or STOPPING. Ignoring start request.");
            return;
        }

        isRunning.set(true);
        pagesCrawled.set(0);
        pagesFailed.set(0);
        startedAt = Instant.now();

        // 1. Seed the frontier queue at depth 0
        List<String> seeds = (seedUrls != null && !seedUrls.isEmpty()) ? seedUrls : properties.seedUrls();
        int seededCount = 0;
        for (String seed : seeds) {
            if (frontier.enqueue(seed, 0)) {
                seededCount++;
            }
        }

        log.info("Crawl started with {} seeds (seeded={}, maxDepth={}, maxPages={})",
                seeds.size(), seededCount, properties.maxDepth(), properties.maxPages());

        // 2. Core Crawling Loop
        while (isRunning.get() && !frontier.isEmpty() && pagesCrawled.get() < properties.maxPages()) {
            Optional<CrawlTask> taskOpt = frontier.poll();
            if (taskOpt.isEmpty()) {
                break;
            }

            CrawlTask task = taskOpt.get();
            try {
                processTask(task);
            } catch (Exception e) {
                log.error("Unhandled exception processing task '{}': {}", task.url(), e.getMessage(), e);
                pagesFailed.incrementAndGet();
            }
        }

        // 3. Finalize state
        if (!isRunning.get()) {
            state.set(CrawlState.STOPPED);
            log.info("Crawl STOPPED. Total crawled: {}, failed: {}, remaining in queue: {}",
                    pagesCrawled.get(), pagesFailed.get(), frontier.size());
        } else {
            state.set(CrawlState.COMPLETED);
            log.info("Crawl COMPLETED. Total crawled: {}, failed: {}, remaining in queue: {}",
                    pagesCrawled.get(), pagesFailed.get(), frontier.size());
        }
        isRunning.set(false);
    }

    /**
     * Processes a single CrawlTask from the frontier.
     */
    private void processTask(CrawlTask task) {
        String url = task.url();
        int depth = task.depth();

        // A. Compliance check with Robots.txt
        if (!robotsTxtService.isAllowed(url)) {
            log.debug("Skipping URL disallowed by robots.txt: {}", url);
            return;
        }

        // B. Politeness rate limiting
        String domain = urlNormalizer.extractDomain(url).orElse("unknown");
        long delayMs = robotsTxtService.getCrawlDelayMs(url).orElse(properties.crawlDelayMs());
        rateLimiter.delayIfNeeded(domain, delayMs);

        // C. Fetch raw HTML document
        Optional<FetchedDocument> fetchedOpt = fetcher.fetch(url);
        if (fetchedOpt.isEmpty()) {
            pagesFailed.incrementAndGet();
            return;
        }

        // D. Parse HTML, extract text, and discover links
        ParsedDocument parsed = htmlParser.parse(fetchedOpt.get());

        // E. Content-Hash deduplication: skip saving if exact text body is already in DB
        if (repository.existsByContentHash(parsed.contentHash())) {
            log.debug("Skipping save for '{}': duplicate content hash '{}' already stored", url, parsed.contentHash());
        } else {
            CrawledDocument entity = new CrawledDocument(
                    parsed.url(),
                    parsed.domain(),
                    parsed.title(),
                    parsed.cleanText(),
                    parsed.contentHash(),
                    parsed.httpStatusCode(),
                    parsed.crawledAt()
            );
            repository.save(entity);
            pagesCrawled.incrementAndGet();
            log.info("Persisted document #{}: [{}] {} (depth={})",
                    pagesCrawled.get(), parsed.domain(), parsed.title(), depth);
        }

        // F. Enqueue outbound links for subsequent BFS hop (if depth < maxDepth)
        if (depth < properties.maxDepth()) {
            for (String link : parsed.outboundLinks()) {
                frontier.enqueue(link, depth + 1, url);
            }
        }
    }

    /**
     * Signals the crawling loop to gracefully shut down.
     */
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            state.set(CrawlState.STOPPING);
            log.info("Stop signal issued to CrawlEngine. Waiting for active loop iteration to finish.");
        }
    }

    /**
     * Returns a snapshot of current telemetry and progress.
     */
    public CrawlStatus getStatus() {
        Duration elapsed = (startedAt != null)
                ? Duration.between(startedAt, Instant.now())
                : Duration.ZERO;

        return new CrawlStatus(
                state.get(),
                pagesCrawled.get(),
                pagesFailed.get(),
                frontier.size(),
                startedAt,
                elapsed
        );
    }

    /**
     * Resets engine state, clearing the queue and in-memory caches.
     */
    public void reset() {
        stop();
        frontier.clear();
        visitedFilter.clearMemory();
        robotsTxtService.clearCache();
        rateLimiter.clear();
        pagesCrawled.set(0);
        pagesFailed.set(0);
        startedAt = null;
        state.set(CrawlState.IDLE);
    }
}
