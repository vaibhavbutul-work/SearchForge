package com.searchforge.indexer.service;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.entity.IndexedDocumentMetadata;
import com.searchforge.indexer.entity.IndexedTerm;
import com.searchforge.indexer.entity.TermPosting;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.repository.IndexedDocumentMetadataRepository;
import com.searchforge.indexer.repository.IndexedTermRepository;
import com.searchforge.indexer.repository.TermPostingRepository;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Production implementation of the SearchForge Indexing Engine.
 *
 * Coordinates text processing, persistent relational indexing, and memory cache synchronization.
 */
@Service
public class DefaultIndexingEngine implements IndexingEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultIndexingEngine.class);
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final Tokenizer tokenizer;
    private final InvertedIndex inMemoryIndex;
    private final IndexedTermRepository termRepository;
    private final TermPostingRepository postingRepository;
    private final IndexedDocumentMetadataRepository metadataRepository;
    private final CrawledDocumentRepository crawledDocumentRepository;

    @Autowired
    public DefaultIndexingEngine(Tokenizer tokenizer,
                                 InvertedIndex inMemoryIndex,
                                 IndexedTermRepository termRepository,
                                 TermPostingRepository postingRepository,
                                 IndexedDocumentMetadataRepository metadataRepository,
                                 CrawledDocumentRepository crawledDocumentRepository) {
        this.tokenizer = tokenizer;
        this.inMemoryIndex = inMemoryIndex;
        this.termRepository = termRepository;
        this.postingRepository = postingRepository;
        this.metadataRepository = metadataRepository;
        this.crawledDocumentRepository = crawledDocumentRepository;
    }

    @Override
    @Transactional
    public IndexingResult indexDocument(CrawledDocument document) {
        if (document == null) {
            return IndexingResult.failure(null, "Document cannot be null", 0);
        }
        if (document.getId() == null) {
            return IndexingResult.failure(null, "Document ID cannot be null (must be persisted)", 0);
        }
        return indexDocument(document.getId(), document.getTitle(), document.getCleanText());
    }

    @Override
    @Transactional
    public IndexingResult indexDocument(Long documentId, String title, String content) {
        long startTime = System.currentTimeMillis();

        if (documentId == null) {
            return IndexingResult.failure(null, "Document ID cannot be null", 0);
        }

        try {
            // Idempotency: If document was already indexed, purge previous persistent SQL postings
            // while preserving in-memory state until atomic replacement
            if (metadataRepository.existsByDocumentId(documentId)) {
                purgePersistentPostings(documentId);
            }

            // Assemble text from title and clean content
            StringBuilder sb = new StringBuilder();
            if (title != null && !title.isBlank()) {
                sb.append(title.trim());
            }
            if (content != null && !content.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(" ");
                }
                sb.append(content.trim());
            }
            String rawText = sb.toString();

            // Run tokenization & linguistic processing pipeline
            List<ProcessedToken> tokens = tokenizer.tokenize(rawText);
            int tokenCount = tokens.size();

            // Group token positions by term in deterministic sorted order to prevent lock-ordering deadlocks
            Map<String, List<Integer>> termPositions = new TreeMap<>();
            for (ProcessedToken token : tokens) {
                termPositions.computeIfAbsent(token.term(), k -> new ArrayList<>()).add(token.position());
            }

            // Persist terms and postings
            for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
                String termStr = entry.getKey();
                List<Integer> positions = entry.getValue();
                int tf = positions.size();

                IndexedTerm term = termRepository.findByTerm(termStr)
                        .map(existing -> {
                            existing.setDocumentFrequency(existing.getDocumentFrequency() + 1);
                            existing.setTotalTermFrequency(existing.getTotalTermFrequency() + tf);
                            return termRepository.save(existing);
                        })
                        .orElseGet(() -> termRepository.save(new IndexedTerm(termStr, 1, tf)));

                TermPosting posting = new TermPosting(term, documentId, positions);
                postingRepository.save(posting);
            }

            // Save document metadata
            IndexedDocumentMetadata metadata = new IndexedDocumentMetadata(documentId, tokenCount, Instant.now());
            metadataRepository.save(metadata);

            // Synchronize with In-Memory Inverted Index atomically
            inMemoryIndex.replaceDocument(documentId, tokens);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Indexed doc {}: {} tokens, {} unique terms in {} ms",
                    documentId, tokenCount, termPositions.size(), duration);

            return IndexingResult.success(documentId, tokenCount, termPositions.size(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to index document {}: {}", documentId, e.getMessage(), e);
            return IndexingResult.failure(documentId, e.getMessage(), duration);
        }
    }

    @Override
    @Transactional
    public void removeDocument(Long documentId) {
        if (documentId == null || !metadataRepository.existsByDocumentId(documentId)) {
            return;
        }

        // Purge persistent relational postings and metadata
        purgePersistentPostings(documentId);

        // Remove from memory cache
        inMemoryIndex.removeDocument(documentId);
        log.debug("Removed document {} from index and persistent storage", documentId);
    }

    private void purgePersistentPostings(Long documentId) {
        // Determine term statistic updates and obsolete terms
        List<TermPosting> postings = postingRepository.findByDocumentId(documentId);
        List<IndexedTerm> termsToDelete = new ArrayList<>();
        List<IndexedTerm> termsToUpdate = new ArrayList<>();

        for (TermPosting posting : postings) {
            IndexedTerm term = posting.getTerm();
            int tf = posting.getTermFrequency();
            int newDf = term.getDocumentFrequency() - 1;
            long newCtf = term.getTotalTermFrequency() - tf;

            if (newDf <= 0) {
                termsToDelete.add(term);
            } else {
                term.setDocumentFrequency(newDf);
                term.setTotalTermFrequency(Math.max(0, newCtf));
                termsToUpdate.add(term);
            }
        }

        // 1. Delete child postings from persistence context and database first
        postingRepository.deleteAll(postings);
        postingRepository.flush();

        // 2. Now safely delete obsolete terms only if no concurrent document references them
        for (IndexedTerm term : termsToDelete) {
            if (!postingRepository.existsByTermId(term.getId())) {
                try {
                    termRepository.delete(term);
                    termRepository.flush();
                } catch (Exception e) {
                    log.debug("Concurrent posting reference prevented deletion of term {}", term.getTerm());
                }
            }
        }
        if (!termsToUpdate.isEmpty()) {
            termsToUpdate.sort(Comparator.comparing(IndexedTerm::getTerm));
            termRepository.saveAll(termsToUpdate);
            termRepository.flush();
        }

        // 3. Delete document metadata
        metadataRepository.deleteById(documentId);
        metadataRepository.flush();
    }

    @Override
    public CorpusSyncResult synchronizeCorpus() {
        return synchronizeCorpus(DEFAULT_BATCH_SIZE);
    }

    @Override
    public CorpusSyncResult synchronizeCorpus(int batchSize) {
        long startTime = System.currentTimeMillis();
        int safeBatchSize = Math.max(1, batchSize);

        int totalProcessed = 0;
        int totalIndexed = 0;
        int totalSkipped = 0;
        long totalTokens = 0;

        Pageable pageable = PageRequest.of(0, safeBatchSize);
        while (true) {
            List<CrawledDocument> unindexedBatch = crawledDocumentRepository.findUnindexedDocuments(pageable);
            if (unindexedBatch.isEmpty()) {
                break;
            }

            for (CrawledDocument doc : unindexedBatch) {
                totalProcessed++;
                try {
                    IndexingResult result = indexDocument(doc);
                    if (result.success()) {
                        totalIndexed++;
                        totalTokens += result.tokenCount();
                    } else {
                        totalSkipped++;
                    }
                } catch (Exception e) {
                    log.warn("Error indexing document {}: {}", doc.getId(), e.getMessage());
                    totalSkipped++;
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Corpus synchronization completed: {} processed, {} indexed, {} skipped, {} tokens in {} ms",
                totalProcessed, totalIndexed, totalSkipped, totalTokens, duration);

        return new CorpusSyncResult(totalProcessed, totalIndexed, totalSkipped, totalTokens, duration);
    }

    @Override
    @Transactional
    public CorpusSyncResult rebuildIndex() {
        log.info("Starting complete inverted index rebuild...");
        postingRepository.deleteAllInBatch();
        termRepository.deleteAllInBatch();
        metadataRepository.deleteAllInBatch();
        inMemoryIndex.clear();

        return synchronizeCorpus();
    }

    @Override
    @Transactional(readOnly = true)
    public void warmupInMemoryIndex() {
        log.info("Warming up in-memory inverted index from persistent storage...");
        inMemoryIndex.clear();

        List<IndexedDocumentMetadata> allMeta = metadataRepository.findAll();
        List<TermPosting> allPostings = postingRepository.findAll();

        Map<Long, Map<String, List<Integer>>> docTermPositions = new HashMap<>();
        for (TermPosting posting : allPostings) {
            docTermPositions.computeIfAbsent(posting.getDocumentId(), k -> new HashMap<>())
                    .put(posting.getTerm().getTerm(), posting.getPositionsList());
        }

        for (IndexedDocumentMetadata meta : allMeta) {
            Map<String, List<Integer>> terms = docTermPositions.getOrDefault(meta.getDocumentId(), Collections.emptyMap());
            inMemoryIndex.addDocumentPostings(meta.getDocumentId(), meta.getTokenCount(), terms);
        }

        log.info("Warmup complete: loaded {} documents, {} vocabulary terms into RAM",
                inMemoryIndex.getTotalDocuments(), inMemoryIndex.getVocabularySize());
    }

    @Override
    @Transactional(readOnly = true)
    public IndexingStats getStats() {
        long totalDocs = metadataRepository.count();
        long totalTerms = termRepository.count();
        long totalPostings = postingRepository.count();
        double avgLength = metadataRepository.findAverageDocumentLength().orElse(0.0);

        return new IndexingStats(totalDocs, totalTerms, totalPostings, avgLength);
    }
}
