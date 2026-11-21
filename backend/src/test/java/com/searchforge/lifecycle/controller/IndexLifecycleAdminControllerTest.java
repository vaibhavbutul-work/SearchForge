package com.searchforge.lifecycle.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.lifecycle.consistency.DocumentEmbeddingConsistencyValidator;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyReport;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyStatus;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.metrics.LifecycleMetricsCollector;
import com.searchforge.lifecycle.metrics.LifecycleSnapshot;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.recovery.DocumentRecoveryResult;
import com.searchforge.lifecycle.recovery.DocumentRecoveryService;
import com.searchforge.lifecycle.recovery.RecoveryBatchResult;
import com.searchforge.lifecycle.repair.IndexRepairResult;
import com.searchforge.lifecycle.repair.IndexRepairService;
import com.searchforge.lifecycle.repair.RepairMode;
import com.searchforge.lifecycle.service.DeletionResult;
import com.searchforge.lifecycle.service.DocumentDeletionService;
import com.searchforge.lifecycle.transaction.StagedIndexingResult;
import com.searchforge.lifecycle.transaction.TransactionalIndexingCoordinator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IndexLifecycleAdminController.class)
@DisplayName("Task 11: Index Lifecycle Admin Controller MVC Tests")
class IndexLifecycleAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CrawledDocumentRepository crawledDocumentRepository;

    @MockitoBean
    private TransactionalIndexingCoordinator indexingCoordinator;

    @MockitoBean
    private DocumentDeletionService deletionService;

    @MockitoBean
    private DocumentEmbeddingConsistencyValidator consistencyValidator;

    @MockitoBean
    private IndexIntegrityChecker integrityChecker;

    @MockitoBean
    private IndexRepairService repairService;

    @MockitoBean
    private DocumentRecoveryService recoveryService;

    @MockitoBean
    private LifecycleMetricsCollector metricsCollector;

    @Test
    @DisplayName("GET /api/v1/lifecycle/status/{docId} returns 200 OK with document status")
    void testGetDocumentStatus() throws Exception {
        Long docId = 42L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/test", "example.com", "Title", "Content", "hash123", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        EmbeddingConsistencyReport embReport = new EmbeddingConsistencyReport(
                docId, EmbeddingConsistencyStatus.CURRENT, 1, 1, "hash123", "hash123",
                "all-MiniLM-L6-v2", "all-MiniLM-L6-v2", 384, 384, true, true, "OK"
        );

        given(crawledDocumentRepository.findById(docId)).willReturn(Optional.of(doc));
        given(consistencyValidator.validateDocument(docId)).willReturn(embReport);

        mockMvc.perform(get("/api/v1/lifecycle/status/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(42))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.embeddingStatus").value("CURRENT"))
                .andExpect(jsonPath("$.isSearchable").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/lifecycle/status/{docId} returns 404 when document not found")
    void testGetDocumentStatusNotFound() throws Exception {
        given(crawledDocumentRepository.findById(999L)).willReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/lifecycle/status/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/lifecycle/reindex/{docId} returns 200 OK with reindex result")
    void testReindexDocument() throws Exception {
        Long docId = 42L;
        CrawledDocument doc = new CrawledDocument(
                "https://example.com/test", "example.com", "Title", "Content", "hash123", 200, Instant.now(),
                DocumentLifecycleStatus.ACTIVE, 1
        );
        doc.setId(docId);

        given(crawledDocumentRepository.findById(docId)).willReturn(Optional.of(doc));
        given(indexingCoordinator.processDocument(eq(docId), anyString(), anyString()))
                .willReturn(StagedIndexingResult.success(docId, 2, 10, 15L));

        mockMvc.perform(post("/api/v1/lifecycle/reindex/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(42))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.tokenCount").value(10));
    }

    @Test
    @DisplayName("DELETE /api/v1/lifecycle/{docId} returns 200 OK with deletion details")
    void testDeleteDocument() throws Exception {
        Long docId = 42L;
        DeletionResult delResult = DeletionResult.success(docId, "https://example.com/test", false);
        given(deletionService.deleteDocument(eq(docId), eq(false))).willReturn(delResult);

        mockMvc.perform(delete("/api/v1/lifecycle/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(42))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.hardDeleted").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/lifecycle/integrity/check returns 200 OK with health status")
    void testCheckIntegrity() throws Exception {
        IndexIntegrityReport report = new IndexIntegrityReport(
                Instant.now(), 10, 10, 0, 10, List.of(), List.of(), List.of(), List.of()
        );
        given(integrityChecker.checkIntegrity()).willReturn(report);

        mockMvc.perform(get("/api/v1/lifecycle/integrity/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(true))
                .andExpect(jsonPath("$.totalDocumentsChecked").value(10))
                .andExpect(jsonPath("$.totalIssueCount").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/lifecycle/integrity/repair returns 200 OK with repair result")
    void testRepairIntegrity() throws Exception {
        IndexIntegrityReport report = new IndexIntegrityReport(
                Instant.now(), 10, 10, 0, 10, List.of(), List.of(), List.of(), List.of()
        );
        IndexRepairResult repairResult = new IndexRepairResult(
                RepairMode.REPAIR, 1, 0, 1, 0, 0, report, report, 25L
        );
        given(repairService.executeRepair(RepairMode.REPAIR)).willReturn(repairResult);

        mockMvc.perform(post("/api/v1/lifecycle/integrity/repair?mode=REPAIR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("REPAIR"))
                .andExpect(jsonPath("$.completelyRepaired").value(true))
                .andExpect(jsonPath("$.missingDocumentsReindexed").value(1))
                .andExpect(jsonPath("$.embeddingsRegenerated").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/lifecycle/retry retries eligible documents")
    void testRetry() throws Exception {
        RecoveryBatchResult batchResult = new RecoveryBatchResult(
                1, 1, 0, List.of(new DocumentRecoveryResult(42L, true, DocumentLifecycleStatus.ACTIVE, 0, "OK"))
        );
        given(recoveryService.retryAllEligible()).willReturn(batchResult);

        mockMvc.perform(post("/api/v1/lifecycle/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempted").value(1))
                .andExpect(jsonPath("$.totalSucceeded").value(1))
                .andExpect(jsonPath("$.totalFailed").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/lifecycle/metrics returns operational telemetry snapshot")
    void testGetMetrics() throws Exception {
        LifecycleSnapshot snapshot = new LifecycleSnapshot(
                Instant.now(), Map.of(DocumentLifecycleStatus.ACTIVE, 10L),
                10, 10, 10, 2, 5, 1, 0, 0, 0,
                5.0, 15.0, 2.0, 22.0,
                10, 10, 200
        );
        given(metricsCollector.getSnapshot()).willReturn(snapshot);

        mockMvc.perform(get("/api/v1/lifecycle/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeDocumentCount").value(10))
                .andExpect(jsonPath("$.vectorStorageSize").value(10))
                .andExpect(jsonPath("$.vocabularySize").value(200));
    }
}
