package com.searchforge.crawler.controller;

import com.searchforge.crawler.engine.CrawlEngine;
import com.searchforge.crawler.engine.CrawlState;
import com.searchforge.crawler.engine.CrawlStatus;
import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CrawlerController.class)
class CrawlerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CrawlEngine crawlEngine;

    @MockitoBean
    private CrawledDocumentRepository documentRepository;

    @Test
    @DisplayName("POST /api/v1/crawler/start returns 202 Accepted when crawl starts successfully")
    void startCrawl_shouldReturn202Accepted_whenEngineIsIdle() throws Exception {
        CrawlStatus idleStatus = new CrawlStatus(CrawlState.IDLE, 0, 0, 0, null, Duration.ZERO);
        CrawlStatus runningStatus = new CrawlStatus(CrawlState.RUNNING, 0, 0, 1, Instant.now(), Duration.ZERO);

        given(crawlEngine.getStatus()).willReturn(idleStatus, runningStatus);
        given(crawlEngine.startAsync(any())).willReturn(CompletableFuture.completedFuture(runningStatus));

        String requestJson = """
                {
                    "seedUrls": ["https://example.com"]
                }
                """;

        mockMvc.perform(post("/api/v1/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.isRunning").value(true));

        verify(crawlEngine).startAsync(List.of("https://example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/crawler/start returns 409 Conflict when crawl is already running")
    void startCrawl_shouldReturn409Conflict_whenAlreadyRunning() throws Exception {
        CrawlStatus runningStatus = new CrawlStatus(CrawlState.RUNNING, 10, 0, 5, Instant.now(), Duration.ofSeconds(30));
        given(crawlEngine.getStatus()).willReturn(runningStatus);

        mockMvc.perform(post("/api/v1/crawler/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Crawl engine is already active"))
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    @DisplayName("POST /api/v1/crawler/stop returns 200 OK and triggers engine.stop()")
    void stopCrawl_shouldReturn200Ok() throws Exception {
        CrawlStatus stoppedStatus = new CrawlStatus(CrawlState.STOPPED, 15, 1, 0, Instant.now(), Duration.ofSeconds(45));
        given(crawlEngine.getStatus()).willReturn(stoppedStatus);

        mockMvc.perform(post("/api/v1/crawler/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STOPPED"))
                .andExpect(jsonPath("$.isRunning").value(false))
                .andExpect(jsonPath("$.pagesCrawled").value(15));

        verify(crawlEngine).stop();
    }

    @Test
    @DisplayName("POST /api/v1/crawler/reset returns 200 OK and resets engine")
    void resetCrawl_shouldReturn200Ok() throws Exception {
        CrawlStatus idleStatus = new CrawlStatus(CrawlState.IDLE, 0, 0, 0, null, Duration.ZERO);
        given(crawlEngine.getStatus()).willReturn(idleStatus);

        mockMvc.perform(post("/api/v1/crawler/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.pagesCrawled").value(0));

        verify(crawlEngine).reset();
    }

    @Test
    @DisplayName("GET /api/v1/crawler/status returns 200 OK with telemetry snapshot")
    void getStatus_shouldReturn200OkWithTelemetry() throws Exception {
        Instant startTime = Instant.parse("2026-11-18T20:00:00Z");
        CrawlStatus telemetry = new CrawlStatus(
                CrawlState.COMPLETED,
                42,
                2,
                0,
                startTime,
                Duration.ofSeconds(120)
        );

        given(crawlEngine.getStatus()).willReturn(telemetry);

        mockMvc.perform(get("/api/v1/crawler/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.isRunning").value(false))
                .andExpect(jsonPath("$.pagesCrawled").value(42))
                .andExpect(jsonPath("$.pagesFailed").value(2))
                .andExpect(jsonPath("$.frontierQueueSize").value(0))
                .andExpect(jsonPath("$.startedAt").value("2026-11-18T20:00:00Z"))
                .andExpect(jsonPath("$.elapsedSeconds").value(120));
    }

    @Test
    @DisplayName("GET /api/v1/crawler/documents returns paginated document summaries with snippets")
    void listDocuments_shouldReturnPaginatedSummaries() throws Exception {
        CrawledDocument doc1 = new CrawledDocument(
                "https://example.com/page1",
                "example.com",
                "Title One",
                "This is the body content of page one that should be summarized nicely.",
                "hash1",
                200,
                Instant.parse("2026-11-18T21:00:00Z")
        );

        PageImpl<CrawledDocument> pagedResult = new PageImpl<>(List.of(doc1));
        given(documentRepository.findAll(any(Pageable.class))).willReturn(pagedResult);

        mockMvc.perform(get("/api/v1/crawler/documents?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].url").value("https://example.com/page1"))
                .andExpect(jsonPath("$.content[0].domain").value("example.com"))
                .andExpect(jsonPath("$.content[0].title").value("Title One"))
                .andExpect(jsonPath("$.content[0].contentSnippet").value("This is the body content of page one that should be summarized nicely."))
                .andExpect(jsonPath("$.content[0].httpStatusCode").value(200))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
