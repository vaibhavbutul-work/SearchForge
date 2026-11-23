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
    private static final Object NEW_TERM_MUTEX = new Object();

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
        return indexDocument(documentId, title, content, null);
    }

    @Override
    @Transactional
    public IndexingResult indexDocument(Long documentId, String title, String content, List<ProcessedToken> precomputedTokens) {
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

            // Reuse precomputed tokens if available, or run tokenization pipeline
            List<ProcessedToken> tokens;
            if (precomputedTokens != null) {
                tokens = precomputedTokens;
            } else {
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
                tokens = tokenizer.tokenize(sb.toString());
            }

            int tokenCount = tokens.size();

            // Group token positions by term in deterministic sorted order to prevent lock-ordering deadlocks
            Map<String, List<Integer>> termPositions = new TreeMap<>();
            for (ProcessedToken token : tokens) {
                termPositions.computeIfAbsent(token.term(), k -> new ArrayList<>()).add(token.position());
            }

            // BATCH PERSISTENCE OPTIMIZATION:
            // 1. Batch fetch all existing terms in one single query
            List<String> termNames = new ArrayList<>(termPositions.keySet());
            List<IndexedTerm> existingTerms = termRepository.findAllByTermIn(termNames);
            Map<String, IndexedTerm> termMap = new HashMap<>(existingTerms.size());
            for (IndexedTerm t : existingTerms) {
                termMap.put(t.getTerm(), t);
            }

            // 2. Identify missing terms and insert them safely without duplicate key races
            List<String> missingTermNames = new ArrayList<>();
            for (String termStr : termPositions.keySet()) {
                if (!termMap.containsKey(termStr)) {
                    missingTermNames.add(termStr);
                }
            }

            if (!missingTermNames.isEmpty()) {
                synchronized (NEW_TERM_MUTEX) {
                    List<IndexedTerm> freshLookup = termRepository.findAllByTermIn(missingTermNames);
                    for (IndexedTerm t : freshLookup) {
                        termMap.put(t.getTerm(), t);
                    }
                    List<IndexedTerm> toInsert = new ArrayList<>();
                    for (String missing : missingTermNames) {
                        if (!termMap.containsKey(missing)) {
                            int tf = termPositions.get(missing).size();
                            IndexedTerm term = new IndexedTerm(missing, 1, tf);
                            toInsert.add(term);
                            termMap.put(missing, term);
                        }
                    }
                    if (!toInsert.isEmpty()) {
                        List<IndexedTerm> inserted = termRepository.saveAllAndFlush(toInsert);
                        for (IndexedTerm t : inserted) {
                            termMap.put(t.getTerm(), t);
                        }
                    }
                }
            }

            // 3. For all already-existing terms, update document frequency and collection term frequency
            List<IndexedTerm> existingToUpdate = new ArrayList<>();
            for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
                String termStr = entry.getKey();
                if (!missingTermNames.contains(termStr)) {
                    IndexedTerm term = termMap.get(termStr);
                    if (term != null) {
                        term.setDocumentFrequency(term.getDocumentFrequency() + 1);
                        term.setTotalTermFrequency(term.getTotalTermFrequency() + entry.getValue().size());
                        existingToUpdate.add(term);
                    }
                }
            }
            if (!existingToUpdate.isEmpty()) {
                termRepository.saveAll(existingToUpdate);
            }

            // 4. Batch construct and save all postings in one JDBC round-trip
            List<TermPosting> postingsToSave = new ArrayList<>(termPositions.size());
            for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
                IndexedTerm term = termMap.get(entry.getKey());
                postingsToSave.add(new TermPosting(term, documentId, entry.getValue()));
            }
            postingRepository.saveAll(postingsToSave);

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
