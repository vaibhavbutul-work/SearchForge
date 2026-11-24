package com.searchforge.crawler.controller;

import com.searchforge.crawler.controller.dto.CrawlStartRequest;
import com.searchforge.crawler.controller.dto.CrawlStatusResponse;
import com.searchforge.crawler.controller.dto.CrawledDocumentSummaryResponse;
import com.searchforge.crawler.engine.CrawlEngine;
import com.searchforge.crawler.engine.CrawlState;
import com.searchforge.crawler.engine.CrawlStatus;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API controller providing administrative control and monitoring endpoints for the Web Crawler.
 */
@RestController
@RequestMapping("/api/v1/crawler")
public class CrawlerController {

    private static final Logger log = LoggerFactory.getLogger(CrawlerController.class);

    public static final int MAX_SEED_URLS = 50;
    public static final int MAX_URL_LENGTH = 2048;

    private final CrawlEngine crawlEngine;
    private final CrawledDocumentRepository documentRepository;

    public CrawlerController(CrawlEngine crawlEngine, CrawledDocumentRepository documentRepository) {
        this.crawlEngine = crawlEngine;
        this.documentRepository = documentRepository;
    }

    /**
     * Initiates an asynchronous crawl in the background.
     *
     * @param request Optional custom seed URLs.
     * @return 202 Accepted if started, or 409 Conflict if a crawl is already running.
     */
    @PostMapping("/start")
    public ResponseEntity<?> startCrawl(@RequestBody(required = false) CrawlStartRequest request) {
        if (request != null && request.seedUrls() != null) {
            if (request.seedUrls().size() > MAX_SEED_URLS) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Maximum " + MAX_SEED_URLS + " seed URLs allowed per crawl request"
                ));
            }
            for (String seedUrl : request.seedUrls()) {
                if (seedUrl != null && seedUrl.length() > MAX_URL_LENGTH) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Seed URL exceeds maximum allowed length of " + MAX_URL_LENGTH + " characters"
                    ));
                }
            }
        }

        CrawlStatus currentStatus = crawlEngine.getStatus();
        if (currentStatus.isRunning() || currentStatus.state() == CrawlState.STOPPING) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Crawl engine is already active",
                            "state", currentStatus.state().name()
                    ));
        }

        var seeds = (request != null && !request.seedUrls().isEmpty()) ? request.seedUrls() : null;
        crawlEngine.startAsync(seeds);

        log.info("REST request initiated async crawl with seeds: {}", seeds);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(CrawlStatusResponse.from(crawlEngine.getStatus()));
    }

    /**
     * Gracefully requests the running crawler to stop.
     *
     * @return 200 OK with the updated status.
     */
    @PostMapping("/stop")
    public ResponseEntity<CrawlStatusResponse> stopCrawl() {
        crawlEngine.stop();
        log.info("REST request issued stop signal to CrawlEngine.");
        return ResponseEntity.ok(CrawlStatusResponse.from(crawlEngine.getStatus()));
    }

    /**
     * Resets the crawler engine state, clearing the frontier and visited cache.
     *
     * @return 200 OK with the idle status.
     */
    @PostMapping("/reset")
    public ResponseEntity<CrawlStatusResponse> resetCrawl() {
        crawlEngine.reset();
        log.info("REST request reset CrawlEngine state.");
        return ResponseEntity.ok(CrawlStatusResponse.from(crawlEngine.getStatus()));
    }

    /**
     * Returns real-time telemetry and operational metrics of the crawler.
     */
    @GetMapping("/status")
    public ResponseEntity<CrawlStatusResponse> getStatus() {
        return ResponseEntity.ok(CrawlStatusResponse.from(crawlEngine.getStatus()));
    }

    /**
     * Returns a paginated list of crawled documents persisted in PostgreSQL.
     */
    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments(
            @PageableDefault(size = 20, sort = "crawledAt", direction = Sort.Direction.DESC) Pageable pageable,
            jakarta.servlet.http.HttpServletRequest request) {
        String pageParam = request.getParameter("page");
        if (pageParam != null) {
            try {
                if (Integer.parseInt(pageParam.trim()) < 0) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Page index must not be negative"
                    ));
                }
            } catch (NumberFormatException ignored) {
                // Handled by Spring or type mismatch
            }
        }

        if (pageable.getPageSize() > 100) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Page size must not exceed 100"
            ));
        }
        if (pageable.getPageNumber() < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Page index must not be negative"
            ));
        }

        Page<CrawledDocumentSummaryResponse> page = documentRepository.findAll(pageable)
                .map(CrawledDocumentSummaryResponse::from);
        return ResponseEntity.ok(page);
    }
}
