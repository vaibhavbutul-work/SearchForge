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
import com.searchforge.crawler.url.UrlTracker;
import com.searchforge.crawler.url.VisitedFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private final UrlTracker urlTracker;

    private final AtomicReference<CrawlState> state = new AtomicReference<>(CrawlState.IDLE);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger pagesCrawled = new AtomicInteger(0);
    private final AtomicInteger pagesFailed = new AtomicInteger(0);
    private final Set<String> visitedContentHashes = ConcurrentHashMap.newKeySet();
    private volatile Instant startedAt;

    @org.springframework.beans.factory.annotation.Autowired
    public CrawlEngine(CrawlerProperties properties,
                       UrlFrontier frontier,
                       VisitedFilter visitedFilter,
                       RobotsTxtService robotsTxtService,
                       PolitenessRateLimiter rateLimiter,
                       DocumentFetcher fetcher,
                       HtmlParser htmlParser,
                       UrlNormalizer urlNormalizer,
                       CrawledDocumentRepository repository,
                       UrlTracker urlTracker) {
        this.properties = properties;
        this.frontier = frontier;
        this.visitedFilter = visitedFilter;
        this.robotsTxtService = robotsTxtService;
        this.rateLimiter = rateLimiter;
        this.fetcher = fetcher;
        this.htmlParser = htmlParser;
        this.urlNormalizer = urlNormalizer;
        this.repository = repository;
        this.urlTracker = urlTracker != null ? urlTracker : frontier.getUrlTracker();
    }

    public CrawlEngine(CrawlerProperties properties,
                       UrlFrontier frontier,
                       VisitedFilter visitedFilter,
                       RobotsTxtService robotsTxtService,
                       PolitenessRateLimiter rateLimiter,
                       DocumentFetcher fetcher,
                       HtmlParser htmlParser,
                       UrlNormalizer urlNormalizer,
                       CrawledDocumentRepository repository) {
        this(properties, frontier, visitedFilter, robotsTxtService, rateLimiter, fetcher, htmlParser, urlNormalizer, repository, frontier.getUrlTracker());
    }

    /**
     * Executes the crawl loop synchronously using configured seed URLs.
     */
    public void runCrawl() {
        runCrawl(properties.seedUrls());
    }

    /**
     * Executes the crawl asynchronously in a Java 21 Virtual Thread.
     * Returns a CompletableFuture that completes when the crawl terminates.
     *
     * @param seedUrls The entry-point URLs to begin crawling.
     * @return CompletableFuture containing the final CrawlStatus.
     */
    public CompletableFuture<CrawlStatus> startAsync(List<String> seedUrls) {
        CompletableFuture<CrawlStatus> future = new CompletableFuture<>();
        Thread.ofVirtual().name("crawl-supervisor").start(() -> {
            try {
                runCrawl(seedUrls);
                future.complete(getStatus());
            } catch (Throwable t) {
                log.error("Crawl supervisor virtual thread encountered an error: {}", t.getMessage(), t);
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Executes the crawl asynchronously using default configured seed URLs.
     */
    public CompletableFuture<CrawlStatus> startAsync() {
        return startAsync(properties.seedUrls());
    }

    /**
     * Executes the crawl loop using Java 21 Virtual Threads for concurrent workers.
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

        int concurrency = properties.concurrency();
        log.info("Crawl started with {} seeds (seeded={}, maxDepth={}, maxPages={}, concurrency={})",
                seeds.size(), seededCount, properties.maxDepth(), properties.maxPages(), concurrency);

        // 2. Concurrent Worker Execution Loop via Java 21 Virtual Threads
        AtomicInteger activeWorkers = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                int workerId = i + 1;
                futures.add(executor.submit(() -> workerLoop(workerId, activeWorkers)));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.error("Virtual worker terminated with error: {}", e.getMessage(), e);
                }
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
     * Worker execution loop run inside a Java 21 Virtual Thread.
     */
    private void workerLoop(int workerId, AtomicInteger activeWorkers) {
        log.debug("Virtual worker-{} started on thread: {}", workerId, Thread.currentThread());
        int emptyPollCount = 0;

        while (isRunning.get() && pagesCrawled.get() < properties.maxPages()) {
            Optional<CrawlTask> taskOpt = frontier.poll();

            if (taskOpt.isPresent()) {
                emptyPollCount = 0;
                activeWorkers.incrementAndGet();
                try {
                    if (pagesCrawled.get() < properties.maxPages()) {
                        processTask(taskOpt.get());
                    }
                } catch (Exception e) {
                    log.error("Worker-{} unhandled exception processing task: {}", workerId, e.getMessage(), e);
                    pagesFailed.incrementAndGet();
                } finally {
                    activeWorkers.decrementAndGet();
                }
            } else {
                // Frontier is empty: check if other workers are still busy and might enqueue more URLs
                if (activeWorkers.get() == 0 && frontier.isEmpty()) {
                    log.debug("Worker-{} exiting: frontier is empty and activeWorkers is 0.", workerId);
                    break;
                }

                // Temporary starve: other workers are still fetching/parsing outbound links.
                // Yield virtual thread cleanly (unmounts virtual thread from OS carrier thread).
                try {
                    Thread.sleep(50);
                    emptyPollCount++;
                    if (emptyPollCount > 100 && activeWorkers.get() == 0) {
                        break;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.debug("Virtual worker-{} finished.", workerId);
    }

    /**
     * Processes a single CrawlTask from the frontier.
     */
    private void processTask(CrawlTask task) {
        String url = task.url();
        int depth = task.depth();

        // 1. Atomically claim in-flight lease
        if (!urlTracker.claimInFlight(url)) {
            log.debug("Skipping URL '{}': already claimed by another worker or completed", url);
            return;
        }

        try {
            // A. Compliance check with Robots.txt
            if (!robotsTxtService.isAllowed(url)) {
                log.debug("Skipping URL disallowed by robots.txt: {}", url);
                urlTracker.markPermanentFailure(url, "Disallowed by robots.txt");
                return;
            }

            // B. Politeness rate limiting
            String domain = urlNormalizer.extractDomain(url).orElse("unknown");
            long delayMs = robotsTxtService.getCrawlDelayMs(url).orElse(properties.crawlDelayMs());
            rateLimiter.delayIfNeeded(domain, delayMs);

            // Check if stopped while waiting on rate limiter
            if (!isRunning.get()) {
                urlTracker.releaseInFlightIfPending(url, "Crawl stopped during rate limiting");
                return;
            }

            // C. Fetch raw HTML document
            Optional<FetchedDocument> fetchedOpt = fetcher.fetch(url);
            if (fetchedOpt.isEmpty()) {
                handleFetchFailure(url, depth, "Fetch failed (timeout or non-2xx status)");
                return;
            }

            // D. Parse HTML, extract text, and discover links
            ParsedDocument parsed = htmlParser.parse(fetchedOpt.get());

            // E. Content-Hash deduplication: skip saving if exact text body is already in memory or DB
            if (!visitedContentHashes.add(parsed.contentHash()) || repository.existsByContentHash(parsed.contentHash())) {
                log.debug("Skipping save for '{}': duplicate content hash '{}' already processed", url, parsed.contentHash());
                urlTracker.markProcessed(url);
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
                try {
                    repository.save(entity);
                    pagesCrawled.incrementAndGet();
                    urlTracker.markProcessed(url);
                    log.info("Persisted document #{}: [{}] {} (depth={})",
                            pagesCrawled.get(), parsed.domain(), parsed.title(), depth);
                } catch (Exception e) {
                    log.warn("Failed to persist document '{}' (possible concurrent duplicate): {}", url, e.getMessage());
                    urlTracker.markProcessed(url);
                }
            }

            // F. Enqueue outbound links for subsequent BFS hop (if depth < maxDepth)
            if (depth < properties.maxDepth()) {
                for (String link : parsed.outboundLinks()) {
                    frontier.enqueue(link, depth + 1, url);
                }
            }
        } finally {
            // Safety guard: ensure lease is not left stranded in IN_FLIGHT state on unhandled exception
            urlTracker.releaseInFlightIfPending(url, "Worker finished");
        }
    }

    private void handleFetchFailure(String url, int depth, String reason) {
        boolean canRetry = urlTracker.markFailedOrRetry(url, properties.maxRetries(), reason);
        if (canRetry && isRunning.get()) {
            log.info("Scheduling retry for failed URL '{}' (attempt, depth={})", url, depth);
            if (properties.retryDelayMs() > 0) {
                try {
                    Thread.sleep(properties.retryDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            frontier.enqueue(url, depth);
        } else {
            pagesFailed.incrementAndGet();
            log.warn("URL '{}' marked as permanently FAILED: {}", url, reason);
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
     * Returns the underlying UrlTracker.
     */
    public UrlTracker getUrlTracker() {
        return urlTracker;
    }

    /**
     * Resets engine state, clearing the queue and in-memory caches.
     */
    public void reset() {
        stop();
        frontier.clear();
        visitedFilter.clearMemory();
        urlTracker.clear();
        visitedContentHashes.clear();
        robotsTxtService.clearCache();
        rateLimiter.clear();
        pagesCrawled.set(0);
        pagesFailed.set(0);
        startedAt = null;
        state.set(CrawlState.IDLE);
    }
}
