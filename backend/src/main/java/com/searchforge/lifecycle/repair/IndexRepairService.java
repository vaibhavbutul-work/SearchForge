package com.searchforge.lifecycle.repair;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.consistency.EmbeddingConsistencyReport;
import com.searchforge.lifecycle.integrity.IndexIntegrityChecker;
import com.searchforge.lifecycle.integrity.IndexIntegrityReport;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.retrieval.DocumentEmbeddingService;
import com.searchforge.semantic.vector.VectorSerialization;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.entity.DocumentEmbeddingEntity;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

/**
 * Automated repair service that restores consistency across SearchForge's storage,
 * lexical indexes, positional indexes, corpus statistics, and semantic embeddings.
 */
@Service
public class IndexRepairService {

    private static final Logger log = LoggerFactory.getLogger(IndexRepairService.class);

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final InvertedIndex inMemoryIndex;
    private final IndexedDocumentMetadataRepository metadataRepository;
    private final TermPostingRepository postingRepository;
    private final IndexingEngine indexingEngine;
    private final DocumentEmbeddingService documentEmbeddingService;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final VectorStorage vectorStorage;
    private final IndexIntegrityChecker integrityChecker;

    public IndexRepairService(
            CrawledDocumentRepository crawledDocumentRepository,
            InvertedIndex inMemoryIndex,
            IndexedDocumentMetadataRepository metadataRepository,
            TermPostingRepository postingRepository,
            IndexingEngine indexingEngine,
            DocumentEmbeddingService documentEmbeddingService,
            DocumentEmbeddingRepository embeddingRepository,
            VectorStorage vectorStorage,
            IndexIntegrityChecker integrityChecker
    ) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository);
        this.inMemoryIndex = Objects.requireNonNull(inMemoryIndex);
        this.metadataRepository = Objects.requireNonNull(metadataRepository);
        this.postingRepository = Objects.requireNonNull(postingRepository);
        this.indexingEngine = Objects.requireNonNull(indexingEngine);
        this.documentEmbeddingService = Objects.requireNonNull(documentEmbeddingService);
        this.embeddingRepository = Objects.requireNonNull(embeddingRepository);
        this.vectorStorage = Objects.requireNonNull(vectorStorage);
        this.integrityChecker = Objects.requireNonNull(integrityChecker);
    }

    /**
     * Executes repair in DRY_RUN or REPAIR mode.
     */
    @Transactional
    public IndexRepairResult executeRepair(RepairMode mode) {
        long startTime = System.currentTimeMillis();
        log.info("Starting index repair in mode: {}", mode);

        IndexIntegrityReport preReport = integrityChecker.checkIntegrity();
        if (preReport.isHealthy()) {
            log.info("Index is already healthy. No repairs needed.");
            long duration = System.currentTimeMillis() - startTime;
            return new IndexRepairResult(mode, 0, 0, 0, 0, 0, preReport, preReport, duration);
        }

        if (mode == RepairMode.DRY_RUN) {
            int plannedReindexes = preReport.missingLexicalDocuments().size();
            int plannedOrphanPurges = preReport.orphanedLexicalDocuments().size();
            int plannedEmbeddingsRegen = 0;
            int plannedOrphanEmbeddings = 0;
            int plannedEmbeddingsSync = 0;

            for (EmbeddingConsistencyReport issue : preReport.embeddingIssues()) {
                switch (issue.status()) {
                    case MISSING, OUTDATED, INCOMPATIBLE -> plannedEmbeddingsRegen++;
                    case ORPHANED -> plannedOrphanEmbeddings++;
                    case DESYNCHRONIZED -> plannedEmbeddingsSync++;
                    default -> {}
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            return new IndexRepairResult(
                    mode,
                    plannedReindexes,
                    plannedOrphanPurges,
                    plannedEmbeddingsRegen,
                    plannedOrphanEmbeddings,
                    plannedEmbeddingsSync,
                    preReport,
                    null,
                    duration
            );
        }

        // Mode is REPAIR: execute corrective actions
        int missingReindexed = 0;
        int orphanedPurged = 0;
        int embeddingsRegen = 0;
        int orphanedEmbeddings = 0;
        int embeddingsSynced = 0;

        // 1. Re-index missing lexical documents
        for (Long docId : preReport.missingLexicalDocuments()) {
            Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(docId);
            if (docOpt.isPresent() && docOpt.get().getStatus() == DocumentLifecycleStatus.ACTIVE) {
                CrawledDocument doc = docOpt.get();
                try {
                    indexingEngine.indexDocument(doc.getId(), doc.getTitle(), doc.getContent());
                    missingReindexed++;
                    log.info("Re-indexed missing lexical document {}", docId);
                } catch (Exception e) {
                    log.error("Failed to re-index missing document {}: {}", docId, e.getMessage(), e);
                }
            }
        }

        // 2. Purge orphaned lexical postings
        for (Long orphanedId : preReport.orphanedLexicalDocuments()) {
            try {
                inMemoryIndex.removeDocument(orphanedId);
                metadataRepository.deleteById(orphanedId);
                postingRepository.deleteByDocumentId(orphanedId);
                orphanedPurged++;
                log.info("Purged orphaned lexical postings for doc {}", orphanedId);
            } catch (Exception e) {
                log.error("Failed to purge orphaned doc {}: {}", orphanedId, e.getMessage(), e);
            }
        }

        // 3. Repair embedding issues
        for (EmbeddingConsistencyReport issue : preReport.embeddingIssues()) {
            Long docId = issue.documentId();
            if (docId == null) continue;

            switch (issue.status()) {
                case MISSING, OUTDATED, INCOMPATIBLE -> {
                    Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(docId);
                    if (docOpt.isPresent() && docOpt.get().getStatus() == DocumentLifecycleStatus.ACTIVE) {
                        CrawledDocument doc = docOpt.get();
                        try {
                            documentEmbeddingService.generateAndStoreEmbedding(
                                    doc.getId(), doc.getTitle(), doc.getContent(),
                                    doc.getContentHash(), doc.getVersion()
                            );
                            embeddingsRegen++;
                            log.info("Regenerated embedding for document {} (status: {})", docId, issue.status());
                        } catch (Exception e) {
                            log.error("Failed to regenerate embedding for doc {}: {}", docId, e.getMessage(), e);
                        }
                    }
                }
                case ORPHANED -> {
                    try {
                        embeddingRepository.deleteByDocumentId(docId);
                        vectorStorage.remove(docId);
                        orphanedEmbeddings++;
                        log.info("Purged orphaned embedding for document {}", docId);
                    } catch (Exception e) {
                        log.error("Failed to purge orphaned embedding for doc {}: {}", docId, e.getMessage(), e);
                    }
                }
                case DESYNCHRONIZED -> {
                    try {
                        if (issue.inDatabase() && !issue.inVectorStorage()) {
                            embeddingRepository.findByDocumentId(docId).ifPresent(entity -> {
                                float[] vector = VectorSerialization.fromByteArray(entity.getVectorData());
                                vectorStorage.put(docId, vector);
                            });
                            embeddingsSynced++;
                            log.info("Synchronized DB embedding to vector storage for document {}", docId);
                        } else if (!issue.inDatabase()) {
                            Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(docId);
                            if (docOpt.isPresent() && docOpt.get().getStatus() == DocumentLifecycleStatus.ACTIVE) {
                                CrawledDocument doc = docOpt.get();
                                documentEmbeddingService.generateAndStoreEmbedding(
                                        doc.getId(), doc.getTitle(), doc.getContent(),
                                        doc.getContentHash(), doc.getVersion()
                                );
                                embeddingsRegen++;
                            } else {
                                vectorStorage.remove(docId);
                                orphanedEmbeddings++;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Failed to synchronize embedding for doc {}: {}", docId, e.getMessage(), e);
                    }
                }
                default -> {}
            }
        }

        // 4. Warmup / re-sync in-memory index if corpus statistics were drifted
        if (!preReport.statisticsDiscrepancies().isEmpty() || missingReindexed > 0 || orphanedPurged > 0) {
            try {
                indexingEngine.warmupInMemoryIndex();
                log.info("Re-warmed in-memory inverted index to synchronize corpus statistics");
            } catch (Exception e) {
                log.warn("Could not re-warm in-memory index: {}", e.getMessage());
            }
        }

        // 5. Post-repair audit
        IndexIntegrityReport postReport = integrityChecker.checkIntegrity();
        long duration = System.currentTimeMillis() - startTime;

        log.info("Repair operation completed in {} ms: reindexed={}, purgedPostings={}, regenEmbeddings={}, purgedEmbeddings={}, syncedEmbeddings={}. Healthy={}",
                duration, missingReindexed, orphanedPurged, embeddingsRegen, orphanedEmbeddings, embeddingsSynced, postReport.isHealthy());

        return new IndexRepairResult(
                mode,
                missingReindexed,
                orphanedPurged,
                embeddingsRegen,
                orphanedEmbeddings,
                embeddingsSynced,
                preReport,
                postReport,
                duration
        );
    }
}
