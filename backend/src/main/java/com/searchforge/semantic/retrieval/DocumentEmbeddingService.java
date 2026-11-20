package com.searchforge.semantic.retrieval;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.VectorSerialization;
import com.searchforge.semantic.vector.VectorStorage;
import com.searchforge.semantic.vector.entity.DocumentEmbeddingEntity;
import com.searchforge.semantic.vector.repository.DocumentEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service managing document embedding generation, storage synchronization,
 * batch indexing, and semantic representation strategies.
 *
 * Implements:
 * 1. Weighted title-lead document representation to avoid semantic dilution.
 * 2. Model incompatibility detection (prevents mixing vectors across incompatible models).
 * 3. Batch embedding generation for high throughput corpus ingestion.
 * 4. Document deletion and cache synchronization.
 */
@Service
public class DocumentEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentEmbeddingService.class);
    private static final int MAX_REPRESENTATION_WORDS = 250;

    private final EmbeddingModel embeddingModel;
    private final VectorStorage vectorStorage;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final CrawledDocumentRepository crawledDocumentRepository;

    public DocumentEmbeddingService(EmbeddingModel embeddingModel,
                                  VectorStorage vectorStorage,
                                  DocumentEmbeddingRepository embeddingRepository,
                                  CrawledDocumentRepository crawledDocumentRepository) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "EmbeddingModel cannot be null");
        this.vectorStorage = Objects.requireNonNull(vectorStorage, "VectorStorage cannot be null");
        this.embeddingRepository = Objects.requireNonNull(embeddingRepository, "DocumentEmbeddingRepository cannot be null");
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository, "CrawledDocumentRepository cannot be null");
    }

    /**
     * Generates and stores a dense embedding for a single crawled document entity.
     * Updates both the high-speed in-memory vector storage and persistent database table.
     */
    @Transactional
    public DocumentVector generateAndStoreEmbedding(CrawledDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Document cannot be null");
        }
        if (document.getId() == null) {
            throw new IllegalArgumentException("Document ID cannot be null (must be persisted first)");
        }
        return generateAndStoreEmbedding(document.getId(), document.getTitle(), document.getCleanText());
    }

    /**
     * Generates and stores an embedding for the given document metadata and text.
     */
    @Transactional
    public DocumentVector generateAndStoreEmbedding(Long documentId, String title, String content) {
        if (documentId == null || documentId <= 0) {
            throw new IllegalArgumentException("Document ID must be positive, got: " + documentId);
        }

        String representation = buildDocumentRepresentation(title, content);
        float[] vector = embeddingModel.embed(representation);

        DocumentVector docVector = new DocumentVector(documentId, vector);

        // 1. Update In-Memory storage for immediate search availability
        vectorStorage.put(docVector);

        // 2. Persist in database for durability across application restarts
        byte[] binaryData = VectorSerialization.toByteArray(docVector.getValues());
        embeddingRepository.findByDocumentId(documentId).ifPresent(embeddingRepository::delete);

        DocumentEmbeddingEntity newEntity = new DocumentEmbeddingEntity(
                documentId,
                embeddingModel.modelName(),
                vector.length,
                binaryData
        );
        embeddingRepository.save(newEntity);

        log.debug("Indexed embedding for document ID {} ({} dimensions, model: {})",
                documentId, vector.length, embeddingModel.modelName());

        return docVector;
    }

    /**
     * Generates and stores embeddings in batch for improved throughput.
     *
     * @param documents list of CrawledDocument entities
     * @return list of generated DocumentVector instances
     */
    @Transactional
    public List<DocumentVector> embedAndStoreBatch(List<CrawledDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }

        List<CrawledDocument> validDocs = documents.stream()
                .filter(d -> d != null && d.getId() != null && d.getId() > 0)
                .toList();

        if (validDocs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> representations = validDocs.stream()
                .map(d -> buildDocumentRepresentation(d.getTitle(), d.getCleanText()))
                .toList();

        List<float[]> vectors = embeddingModel.embedBatch(representations);
        List<DocumentVector> results = new ArrayList<>(validDocs.size());

        for (int i = 0; i < validDocs.size(); i++) {
            CrawledDocument doc = validDocs.get(i);
            float[] vector = vectors.get(i);
            DocumentVector docVector = new DocumentVector(doc.getId(), vector);

            vectorStorage.put(docVector);

            byte[] binaryData = VectorSerialization.toByteArray(vector);
            embeddingRepository.findByDocumentId(doc.getId()).ifPresent(embeddingRepository::delete);

            DocumentEmbeddingEntity entity = new DocumentEmbeddingEntity(
                    doc.getId(),
                    embeddingModel.modelName(),
                    vector.length,
                    binaryData
            );
            embeddingRepository.save(entity);
            results.add(docVector);
        }

        log.info("Batch embedded and persisted {} documents using model {}", results.size(), embeddingModel.modelName());
        return results;
    }

    /**
     * Deletes embedding for the specified document ID from both in-memory and persistent storage.
     */
    @Transactional
    public void deleteEmbedding(Long documentId) {
        if (documentId == null) {
            return;
        }
        vectorStorage.remove(documentId);
        embeddingRepository.findByDocumentId(documentId).ifPresent(embeddingRepository::delete);
        log.debug("Deleted embedding for document ID {}", documentId);
    }

    /**
     * Builds a focused semantic text representation of the document.
     * Prioritizes the title and prepends it to the lead introductory window (up to ~250 words),
     * preventing topic dilution in long documents while maintaining semantic density.
     */
    public String buildDocumentRepresentation(String title, String content) {
        StringBuilder sb = new StringBuilder();

        if (title != null && !title.isBlank()) {
            String cleanTitle = title.trim();
            sb.append(cleanTitle).append(". ").append(cleanTitle).append(". ");
        }

        if (content != null && !content.isBlank()) {
            String[] words = content.trim().split("\\s+");
            int wordLimit = Math.min(words.length, MAX_REPRESENTATION_WORDS);
            for (int i = 0; i < wordLimit; i++) {
                sb.append(words[i]).append(" ");
            }
        }

        String representation = sb.toString().trim();
        if (representation.isEmpty()) {
            representation = "empty document";
        }
        return representation;
    }

    /**
     * Synchronizes embeddings for all crawled documents.
     * Automatically detects incompatible embeddings (from older models or different dimensions)
     * and regenerates them to maintain vector space integrity.
     *
     * @param batchSize maximum documents to process per batch
     * @return total count of documents newly or re-embedded
     */
    @Transactional
    public int syncCorpusEmbeddings(int batchSize) {
        int safeBatchSize = Math.max(1, batchSize);
        int totalEmbedded = 0;

        Pageable pageable = PageRequest.of(0, safeBatchSize);
        Page<CrawledDocument> page = crawledDocumentRepository.findAll(pageable);

        while (page.hasContent()) {
            List<CrawledDocument> toEmbed = new ArrayList<>();
            for (CrawledDocument doc : page.getContent()) {
                Optional<DocumentEmbeddingEntity> existing = embeddingRepository.findByDocumentId(doc.getId());
                if (existing.isEmpty() || !isEmbeddingCompatible(existing.get())) {
                    toEmbed.add(doc);
                }
            }

            if (!toEmbed.isEmpty()) {
                embedAndStoreBatch(toEmbed);
                totalEmbedded += toEmbed.size();
            }

            if (!page.hasNext()) {
                break;
            }
            page = crawledDocumentRepository.findAll(page.nextPageable());
        }

        log.info("Corpus embedding synchronization completed: {} embeddings synchronized for model '{}'",
                totalEmbedded, embeddingModel.modelName());
        return totalEmbedded;
    }

    /**
     * Checks if a stored embedding entity was generated using the current active model and dimensions.
     */
    public boolean isEmbeddingCompatible(DocumentEmbeddingEntity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getDimensions() == embeddingModel.dimensions()
                && Objects.equals(entity.getModelName(), embeddingModel.modelName());
    }

    /**
     * Loads all persisted embeddings from the database into in-memory VectorStorage.
     * Skips incompatible vectors generated by different models to prevent vector space contamination.
     *
     * @return number of vectors loaded into memory
     */
    public int warmupStorageFromDatabase() {
        List<DocumentEmbeddingEntity> entities = embeddingRepository.findAll();
        int loaded = 0;
        int incompatibleCount = 0;

        for (DocumentEmbeddingEntity entity : entities) {
            if (!isEmbeddingCompatible(entity)) {
                log.warn("Skipping incompatible stored vector for doc ID {} (stored model: {}, dimensions: {}; active: {} / {}D)",
                        entity.getDocumentId(), entity.getModelName(), entity.getDimensions(),
                        embeddingModel.modelName(), embeddingModel.dimensions());
                incompatibleCount++;
                continue;
            }

            try {
                float[] vector = VectorSerialization.fromByteArray(entity.getVectorData());
                vectorStorage.put(new DocumentVector(entity.getDocumentId(), vector));
                loaded++;
            } catch (Exception e) {
                log.warn("Failed to deserialize embedding for document {}: {}", entity.getDocumentId(), e.getMessage());
            }
        }

        log.info("Warmed up VectorStorage: {} vectors loaded into memory (skipped {} incompatible vectors)",
                loaded, incompatibleCount);
        return loaded;
    }

    /**
     * Rebuilds all document embeddings from scratch across the entire repository.
     */
    @Transactional
    public int rebuildAllEmbeddings(int batchSize) {
        log.info("Initiating complete rebuild of document embeddings for model '{}'...", embeddingModel.modelName());
        vectorStorage.clear();
        embeddingRepository.deleteAll();
        return syncCorpusEmbeddings(batchSize);
    }

    public boolean hasEmbedding(Long documentId) {
        return vectorStorage.contains(documentId);
    }
}
