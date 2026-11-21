package com.searchforge.lifecycle.consistency;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.entity.DocumentEmbeddingEntity;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Validates document version and dense embedding consistency.
 *
 * Ensures that dense vectors match the active document text, content hash,
 * version number, and current model architecture dimensions.
 */
@Service
public class DocumentEmbeddingConsistencyValidator {

    private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingConsistencyValidator.class);

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final VectorStorage vectorStorage;
    private final EmbeddingModel embeddingModel;

    public DocumentEmbeddingConsistencyValidator(
            CrawledDocumentRepository crawledDocumentRepository,
            DocumentEmbeddingRepository embeddingRepository,
            VectorStorage vectorStorage,
            EmbeddingModel embeddingModel
    ) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository);
        this.embeddingRepository = Objects.requireNonNull(embeddingRepository);
        this.vectorStorage = Objects.requireNonNull(vectorStorage);
        this.embeddingModel = Objects.requireNonNull(embeddingModel);
    }

    /**
     * Validates embedding consistency for a single document ID.
     */
    @Transactional(readOnly = true)
    public EmbeddingConsistencyReport validateDocument(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("Document ID must not be null");
        }

        Optional<CrawledDocument> docOpt = crawledDocumentRepository.findById(documentId);
        Optional<DocumentEmbeddingEntity> embOpt = embeddingRepository.findByDocumentId(documentId);
        boolean inVectorStorage = vectorStorage.contains(documentId);

        if (docOpt.isEmpty()) {
            if (embOpt.isPresent() || inVectorStorage) {
                return new EmbeddingConsistencyReport(
                        documentId,
                        EmbeddingConsistencyStatus.ORPHANED,
                        null,
                        embOpt.map(DocumentEmbeddingEntity::getDocumentVersion).orElse(null),
                        null,
                        embOpt.map(DocumentEmbeddingEntity::getContentHash).orElse(null),
                        embeddingModel.modelName(),
                        embOpt.map(DocumentEmbeddingEntity::getModelName).orElse(null),
                        embeddingModel.dimensions(),
                        embOpt.map(DocumentEmbeddingEntity::getDimensions).orElse(0),
                        embOpt.isPresent(),
                        inVectorStorage,
                        "Embedding exists for non-existent document ID " + documentId
                );
            }
            return new EmbeddingConsistencyReport(
                    documentId,
                    EmbeddingConsistencyStatus.MISSING,
                    null, null, null, null,
                    embeddingModel.modelName(), null,
                    embeddingModel.dimensions(), 0,
                    false, false,
                    "Document ID " + documentId + " not found"
            );
        }

        return validateDocument(docOpt.get(), embOpt.orElse(null), inVectorStorage);
    }

    /**
     * Validates an existing document entity against its embedding.
     */
    public EmbeddingConsistencyReport validateDocument(
            CrawledDocument doc,
            DocumentEmbeddingEntity embedding,
            boolean inVectorStorage
    ) {
        Long docId = doc.getId();
        boolean inDb = embedding != null;
        String configuredModel = embeddingModel.modelName();
        int configuredDims = embeddingModel.dimensions();

        // If document is DELETED, it must NOT have an embedding
        if (doc.getStatus() == DocumentLifecycleStatus.DELETED) {
            if (inDb || inVectorStorage) {
                return new EmbeddingConsistencyReport(
                        docId,
                        EmbeddingConsistencyStatus.ORPHANED,
                        doc.getVersion(),
                        embedding != null ? embedding.getDocumentVersion() : null,
                        doc.getContentHash(),
                        embedding != null ? embedding.getContentHash() : null,
                        configuredModel,
                        embedding != null ? embedding.getModelName() : null,
                        configuredDims,
                        embedding != null ? embedding.getDimensions() : 0,
                        inDb,
                        inVectorStorage,
                        "Deleted document still has active embedding"
                );
            }
            return new EmbeddingConsistencyReport(
                    docId,
                    EmbeddingConsistencyStatus.CURRENT,
                    doc.getVersion(), null,
                    doc.getContentHash(), null,
                    configuredModel, null,
                    configuredDims, 0,
                    false, false,
                    "Deleted document correctly has no embedding"
            );
        }

        // For ACTIVE documents
        if (doc.getStatus() == DocumentLifecycleStatus.ACTIVE) {
            if (!inDb && !inVectorStorage) {
                return new EmbeddingConsistencyReport(
                        docId,
                        EmbeddingConsistencyStatus.MISSING,
                        doc.getVersion(), null,
                        doc.getContentHash(), null,
                        configuredModel, null,
                        configuredDims, 0,
                        false, false,
                        "Active document has no embedding representation"
                );
            }

            if (!inDb || !inVectorStorage) {
                return new EmbeddingConsistencyReport(
                        docId,
                        EmbeddingConsistencyStatus.DESYNCHRONIZED,
                        doc.getVersion(),
                        embedding != null ? embedding.getDocumentVersion() : null,
                        doc.getContentHash(),
                        embedding != null ? embedding.getContentHash() : null,
                        configuredModel,
                        embedding != null ? embedding.getModelName() : null,
                        configuredDims,
                        embedding != null ? embedding.getDimensions() : 0,
                        inDb,
                        inVectorStorage,
                        inDb ? "Embedding in DB but missing from in-memory VectorStorage"
                             : "Embedding in VectorStorage but missing from relational DB"
                );
            }

            // Model compatibility check
            if (!embedding.getModelName().equalsIgnoreCase(configuredModel) ||
                    embedding.getDimensions() != configuredDims) {
                return new EmbeddingConsistencyReport(
                        docId,
                        EmbeddingConsistencyStatus.INCOMPATIBLE,
                        doc.getVersion(),
                        embedding.getDocumentVersion(),
                        doc.getContentHash(),
                        embedding.getContentHash(),
                        configuredModel,
                        embedding.getModelName(),
                        configuredDims,
                        embedding.getDimensions(),
                        true,
                        true,
                        "Embedding model mismatch: expected " + configuredModel + " (" + configuredDims + " dims) " +
                                "but found " + embedding.getModelName() + " (" + embedding.getDimensions() + " dims)"
                );
            }

            // Version and hash check
            boolean hashMismatch = doc.getContentHash() != null &&
                    embedding.getContentHash() != null &&
                    !doc.getContentHash().equals(embedding.getContentHash());

            boolean versionMismatch = embedding.getDocumentVersion() != null &&
                    doc.getVersion() != embedding.getDocumentVersion();

            if (hashMismatch || versionMismatch) {
                return new EmbeddingConsistencyReport(
                        docId,
                        EmbeddingConsistencyStatus.OUTDATED,
                        doc.getVersion(),
                        embedding.getDocumentVersion(),
                        doc.getContentHash(),
                        embedding.getContentHash(),
                        configuredModel,
                        embedding.getModelName(),
                        configuredDims,
                        embedding.getDimensions(),
                        true,
                        true,
                        "Embedding outdated: doc version=" + doc.getVersion() + ", emb version=" + embedding.getDocumentVersion()
                );
            }

            return new EmbeddingConsistencyReport(
                    docId,
                    EmbeddingConsistencyStatus.CURRENT,
                    doc.getVersion(),
                    embedding.getDocumentVersion(),
                    doc.getContentHash(),
                    embedding.getContentHash(),
                    configuredModel,
                    embedding.getModelName(),
                    configuredDims,
                    embedding.getDimensions(),
                    true,
                    true,
                    "Embedding consistent and synchronized"
            );
        }

        // Other non-active statuses (DISCOVERED, FETCHED, PROCESSING, FAILED, RETRY, etc.)
        return new EmbeddingConsistencyReport(
                docId,
                inDb ? EmbeddingConsistencyStatus.ORPHANED : EmbeddingConsistencyStatus.CURRENT,
                doc.getVersion(),
                embedding != null ? embedding.getDocumentVersion() : null,
                doc.getContentHash(),
                embedding != null ? embedding.getContentHash() : null,
                configuredModel,
                embedding != null ? embedding.getModelName() : null,
                configuredDims,
                embedding != null ? embedding.getDimensions() : 0,
                inDb,
                inVectorStorage,
                "Document in status " + doc.getStatus()
        );
    }

    /**
     * Validates all documents and embeddings across the entire system.
     */
    @Transactional(readOnly = true)
    public List<EmbeddingConsistencyReport> validateAll() {
        List<EmbeddingConsistencyReport> reports = new ArrayList<>();
        List<CrawledDocument> allDocs = crawledDocumentRepository.findAll();
        Set<Long> verifiedDocIds = new HashSet<>();

        for (CrawledDocument doc : allDocs) {
            verifiedDocIds.add(doc.getId());
            Optional<DocumentEmbeddingEntity> embOpt = embeddingRepository.findByDocumentId(doc.getId());
            boolean inVectorStorage = vectorStorage.contains(doc.getId());
            reports.add(validateDocument(doc, embOpt.orElse(null), inVectorStorage));
        }

        // Check for orphaned embeddings in relational DB
        List<DocumentEmbeddingEntity> allEmbeddings = embeddingRepository.findAll();
        for (DocumentEmbeddingEntity emb : allEmbeddings) {
            if (!verifiedDocIds.contains(emb.getDocumentId())) {
                reports.add(new EmbeddingConsistencyReport(
                        emb.getDocumentId(),
                        EmbeddingConsistencyStatus.ORPHANED,
                        null,
                        emb.getDocumentVersion(),
                        null,
                        emb.getContentHash(),
                        embeddingModel.modelName(),
                        emb.getModelName(),
                        embeddingModel.dimensions(),
                        emb.getDimensions(),
                        true,
                        vectorStorage.contains(emb.getDocumentId()),
                        "Orphaned embedding in database for non-existent document ID " + emb.getDocumentId()
                ));
            }
        }

        return reports;
    }
}
