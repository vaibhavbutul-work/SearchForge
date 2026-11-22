package com.searchforge.lifecycle.startup;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.lifecycle.repair.IndexRepairResult;
import com.searchforge.lifecycle.repair.IndexRepairService;
import com.searchforge.lifecycle.repair.RepairMode;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Startup crash reconciliation and auto-healing component.
 *
 * Runs automatically on {@link ApplicationReadyEvent} (or synchronously via {@link #reconcile()}):
 * 1. Seeds {@link ActiveDocumentFilter} with all ACTIVE documents from the database.
 * 2. Pre-warms in-memory lexical inverted index from persistent database postings.
 * 3. Pre-warms in-memory dense vector storage from persistent embedding entities.
 * 4. Audits consistency across relational state, lexical postings, and dense embeddings.
 * 5. Automatically repairs any crash-induced or incomplete indexing defects, restoring
 *    the engine to 100% healthy operational status.
 */
@Component
public class StartupIndexReconciliationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupIndexReconciliationRunner.class);

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final ActiveDocumentFilter activeDocumentFilter;
    private final IndexingEngine indexingEngine;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final IndexIntegrityChecker integrityChecker;
    private final IndexRepairService repairService;

    @Value("${searchforge.lifecycle.startup-reconciliation.enabled:true}")
    private boolean enabled = true;

    public StartupIndexReconciliationRunner(
            CrawledDocumentRepository crawledDocumentRepository,
            ActiveDocumentFilter activeDocumentFilter,
            IndexingEngine indexingEngine,
            DocumentEmbeddingService documentEmbeddingService,
            IndexIntegrityChecker integrityChecker,
            IndexRepairService repairService
    ) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "crawledDocumentRepository cannot be null");
        this.activeDocumentFilter = Objects.requireNonNull(activeDocumentFilter, "activeDocumentFilter cannot be null");
        this.indexingEngine = Objects.requireNonNull(indexingEngine, "indexingEngine cannot be null");
        this.documentEmbeddingService = Objects.requireNonNull(documentEmbeddingService, "documentEmbeddingService cannot be null");
        this.integrityChecker = Objects.requireNonNull(integrityChecker, "integrityChecker cannot be null");
        this.repairService = Objects.requireNonNull(repairService, "repairService cannot be null");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("Startup index reconciliation is disabled via searchforge.lifecycle.startup-reconciliation.enabled=false");
            return;
        }
        reconcile();
    }

    /**
     * Executes the complete startup reconciliation and auto-healing sequence.
     *
     * @return Final {@link IndexIntegrityReport} verifying index state.
     */
    public IndexIntegrityReport reconcile() {
        long startTime = System.currentTimeMillis();
        log.info("Initiating startup index reconciliation and crash auto-healing...");

        // 1. Seed ActiveDocumentFilter from persistent relational state
        List<Long> activeIds = crawledDocumentRepository.findAll().stream()
                .filter(doc -> doc.getStatus() == DocumentLifecycleStatus.ACTIVE)
                .map(CrawledDocument::getId)
                .toList();
        activeDocumentFilter.initialize(activeIds);
        log.info("Seeded ActiveDocumentFilter with {} active documents", activeIds.size());

        // 2. Pre-warm lexical inverted index into RAM
        try {
            indexingEngine.warmupInMemoryIndex();
        } catch (Exception e) {
            log.error("Failed to warmup in-memory lexical index during startup: {}", e.getMessage(), e);
        }

        // 3. Pre-warm vector storage into RAM
        try {
            int vectorCount = documentEmbeddingService.warmupStorageFromDatabase();
            log.info("Pre-warmed {} vector embeddings into in-memory VectorStorage", vectorCount);
        } catch (Exception e) {
            log.error("Failed to warmup in-memory vector storage during startup: {}", e.getMessage(), e);
        }

        // 4. Run integrity audit
        IndexIntegrityReport preReport = integrityChecker.checkIntegrity();
        if (!preReport.isHealthy()) {
            log.warn("Startup index audit found {} consistency defects: {} missing docs, {} orphaned docs, {} stats issues, {} embedding issues",
                    preReport.totalIssueCount(),
                    preReport.missingLexicalDocuments().size(),
                    preReport.orphanedLexicalDocuments().size(),
                    preReport.statisticsDiscrepancies().size(),
                    preReport.embeddingIssues().size());

            // 5. Auto-heal defects via repair service
            IndexRepairResult repairResult = repairService.executeRepair(RepairMode.REPAIR);
            log.info("Startup auto-healing repair completed in {} ms: reindexed={}, purgedPostings={}, regenEmbeddings={}, purgedEmbeddings={}, syncedVectors={}",
                    repairResult.durationMs(),
                    repairResult.missingDocumentsReindexed(),
                    repairResult.orphanedPostingsPurged(),
                    repairResult.embeddingsRegenerated(),
                    repairResult.orphanedEmbeddingsPurged(),
                    repairResult.embeddingsSynchronized());

            // Refresh active filter after repair
            List<Long> refreshedActiveIds = crawledDocumentRepository.findAll().stream()
                    .filter(doc -> doc.getStatus() == DocumentLifecycleStatus.ACTIVE)
                    .map(CrawledDocument::getId)
                    .toList();
            activeDocumentFilter.initialize(refreshedActiveIds);
        }

        IndexIntegrityReport finalReport = integrityChecker.checkIntegrity();
        long duration = System.currentTimeMillis() - startTime;
        log.info("Startup index reconciliation finished in {} ms. Status healthy: {}", duration, finalReport.isHealthy());
        return finalReport;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
