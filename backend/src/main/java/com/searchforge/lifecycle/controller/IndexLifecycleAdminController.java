package com.searchforge.lifecycle.controller;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.lifecycle.consistency.DocumentEmbeddingConsistencyValidator;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyReport;
import com.searchforge.lifecycle.dto.*;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.metrics.LifecycleMetricsCollector;
import com.searchforge.lifecycle.metrics.LifecycleSnapshot;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * REST API administration controller for document lifecycle management,
 * consistency verification, automated repair, and operational telemetry.
 */
@RestController
@RequestMapping("/api/v1/lifecycle")
public class IndexLifecycleAdminController {

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final TransactionalIndexingCoordinator indexingCoordinator;
    private final DocumentDeletionService deletionService;
    private final DocumentEmbeddingConsistencyValidator consistencyValidator;
    private final IndexIntegrityChecker integrityChecker;
    private final IndexRepairService repairService;
    private final DocumentRecoveryService recoveryService;
    private final LifecycleMetricsCollector metricsCollector;

    public IndexLifecycleAdminController(
            CrawledDocumentRepository crawledDocumentRepository,
            TransactionalIndexingCoordinator indexingCoordinator,
            DocumentDeletionService deletionService,
            DocumentEmbeddingConsistencyValidator consistencyValidator,
            IndexIntegrityChecker integrityChecker,
            IndexRepairService repairService,
            DocumentRecoveryService recoveryService,
            LifecycleMetricsCollector metricsCollector
    ) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository);
        this.indexingCoordinator = Objects.requireNonNull(indexingCoordinator);
        this.deletionService = Objects.requireNonNull(deletionService);
        this.consistencyValidator = Objects.requireNonNull(consistencyValidator);
        this.integrityChecker = Objects.requireNonNull(integrityChecker);
        this.repairService = Objects.requireNonNull(repairService);
        this.recoveryService = Objects.requireNonNull(recoveryService);
        this.metricsCollector = Objects.requireNonNull(metricsCollector);
    }

    /**
     * GET /api/v1/lifecycle/status/{docId}
     * Retrieves the lifecycle state and embedding consistency of a document.
     */
    @GetMapping("/status/{docId}")
    public ResponseEntity<LifecycleStatusResponse> getDocumentStatus(@PathVariable Long docId) {
        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CrawledDocument doc = docOpt.get();
        EmbeddingConsistencyReport embReport = consistencyValidator.validateDocument(docId);

        LifecycleStatusResponse response = new LifecycleStatusResponse(
                doc.getId(),
                doc.getUrl(),
                doc.getStatus(),
                doc.getVersion(),
                doc.getRetryCount(),
                doc.getStatusMessage(),
                doc.getLastProcessedAt(),
                embReport.status(),
                doc.isSearchable()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/lifecycle/reindex/{docId}
     * Triggers manual re-indexing and embedding regeneration for a document.
     */
    @PostMapping("/reindex/{docId}")
    public ResponseEntity<DocumentReindexResponse> reindexDocument(@PathVariable Long docId) {
        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CrawledDocument doc = docOpt.get();
        StagedIndexingResult result = indexingCoordinator.processDocument(
                doc.getId(), doc.getTitle(), doc.getCleanText()
        );

        DocumentReindexResponse response = new DocumentReindexResponse(
                docId,
                result.isSuccess(),
                result.version(),
                result.tokenCount(),
                result.durationMs(),
                result.message()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/v1/lifecycle/{docId}?hard=false
     * Deletes a document and immediately removes it from lexical and semantic indexes.
     */
    @DeleteMapping("/{docId}")
    public ResponseEntity<DocumentDeletionResponse> deleteDocument(
            @PathVariable Long docId,
            @RequestParam(defaultValue = "false") boolean hard
    ) {
        long startTime = System.currentTimeMillis();
        DeletionResult result = deletionService.deleteDocument(docId, hard);
        long durationMs = System.currentTimeMillis() - startTime;

        DocumentDeletionResponse response = new DocumentDeletionResponse(
                docId,
                result.isSuccess(),
                result.hardDeleted(),
                durationMs,
                result.message()
        );

        if (result.status() == DeletionResult.Status.NOT_FOUND) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        metricsCollector.recordDeleted();
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/lifecycle/integrity/check
     * Executes non-destructive index reconciliation audit across all layers.
     */
    @GetMapping("/integrity/check")
    public ResponseEntity<IntegrityCheckResponse> checkIntegrity() {
        IndexIntegrityReport report = integrityChecker.checkIntegrity();
        return ResponseEntity.ok(toIntegrityResponse(report));
    }

    /**
     * POST /api/v1/lifecycle/integrity/repair?mode=REPAIR
     * Executes automated repair in DRY_RUN or REPAIR mode.
     */
    @PostMapping("/integrity/repair")
    public ResponseEntity<RepairResponse> repairIntegrity(
            @RequestParam(defaultValue = "REPAIR") RepairMode mode
    ) {
        IndexRepairResult result = repairService.executeRepair(mode);

        IntegrityCheckResponse postStatus = result.postRepairReport() != null
                ? toIntegrityResponse(result.postRepairReport())
                : null;

        RepairResponse response = new RepairResponse(
                result.mode(),
                result.isCompletelyRepaired(),
                result.missingDocumentsReindexed(),
                result.orphanedPostingsPurged(),
                result.embeddingsRegenerated(),
                result.orphanedEmbeddingsPurged(),
                result.embeddingsSynchronized(),
                result.durationMs(),
                postStatus
        );

        if (mode == RepairMode.REPAIR) {
            metricsCollector.recordRepaired();
        }

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/lifecycle/retry?documentId=...
     * Retries failed or retry-eligible documents.
     */
    @PostMapping("/retry")
    public ResponseEntity<RetryResponse> retryDocuments(
            @RequestParam(required = false) Long documentId
    ) {
        if (documentId != null) {
            DocumentRecoveryResult result = recoveryService.retryDocument(documentId);
            RetryResponse response = new RetryResponse(
                    1,
                    result.success() ? 1 : 0,
                    result.success() ? 0 : 1,
                    List.of(result)
            );
            metricsCollector.recordRetried();
            return ResponseEntity.ok(response);
        }

        RecoveryBatchResult batchResult = recoveryService.retryAllEligible();
        RetryResponse response = new RetryResponse(
                batchResult.totalAttempted(),
                batchResult.totalSucceeded(),
                batchResult.totalFailed(),
                batchResult.results()
        );
        for (int i = 0; i < batchResult.totalSucceeded(); i++) {
            metricsCollector.recordRetried();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/lifecycle/metrics
     * Returns operational telemetry metrics.
     */
    @GetMapping("/metrics")
    public ResponseEntity<LifecycleSnapshot> getMetrics() {
        return ResponseEntity.ok(metricsCollector.getSnapshot());
    }

    private IntegrityCheckResponse toIntegrityResponse(IndexIntegrityReport report) {
        List<String> embeddingIssues = report.embeddingIssues().stream()
                .map(issue -> String.format("Doc %d: %s (%s)", issue.documentId(), issue.status(), issue.details()))
                .toList();

        return new IntegrityCheckResponse(
                report.timestamp(),
                report.isHealthy(),
                report.totalDocumentsChecked(),
                report.activeDocumentCount(),
                report.deletedDocumentCount(),
                report.cleanDocuments(),
                report.totalIssueCount(),
                report.missingLexicalDocuments(),
                report.orphanedLexicalDocuments(),
                report.statisticsDiscrepancies(),
                embeddingIssues
        );
    }
}
