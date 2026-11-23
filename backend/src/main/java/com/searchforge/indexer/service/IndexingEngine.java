package com.searchforge.indexer.service;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.indexer.token.ProcessedToken;

import java.util.List;

/**
 * Core interface for the SearchForge Indexing Engine.
 *
 * Orchestrates document tokenization, atomic persistence of terms and postings,
 * synchronization with the in-memory inverted index, and batch corpus synchronization.
 */
public interface IndexingEngine {

    /**
     * Indexes a crawled document from the crawling database.
     * Extracts title and clean text, tokenizes, saves postings and metadata,
     * and syncs the in-memory inverted index.
     *
     * @param document CrawledDocument entity.
     * @return IndexingResult detailing outcome, token count, and duration.
     */
    IndexingResult indexDocument(CrawledDocument document);

    /**
     * Indexes a document given its ID, title, and content.
     *
     * @param documentId Unique identifier of the document.
     * @param title      Document title.
     * @param content    Document content text.
     * @return IndexingResult detailing outcome, token count, and duration.
     */
    IndexingResult indexDocument(Long documentId, String title, String content);

    /**
     * Indexes a document given its ID, title, and content, using precomputed tokens.
     * Eliminates duplicate tokenization across staged indexing passes.
     *
     * @param documentId         Unique identifier of the document.
     * @param title              Document title.
     * @param content            Document content text.
     * @param precomputedTokens  Precomputed token stream from Stage 1.
     * @return IndexingResult detailing outcome, token count, and duration.
     */
    IndexingResult indexDocument(Long documentId, String title, String content, List<ProcessedToken> precomputedTokens);

    /**
     * Removes an indexed document and all its postings from both persistent storage and memory.
     *
     * @param documentId Document ID to remove.
     */
    void removeDocument(Long documentId);

    /**
     * Scans the crawling database for unindexed documents and indexes them in default batches.
     *
     * @return CorpusSyncResult detailing total processed, indexed, and tokens counted.
     */
    CorpusSyncResult synchronizeCorpus();

    /**
     * Scans the crawling database for unindexed documents and indexes them with a specific batch size.
     *
     * @param batchSize Batch size for database chunking.
     * @return CorpusSyncResult detailing total processed, indexed, and tokens counted.
     */
    CorpusSyncResult synchronizeCorpus(int batchSize);

    /**
     * Rebuilds the inverted index from scratch by clearing persistent index tables and in-memory structures,
     * then indexing all crawled documents.
     *
     * @return CorpusSyncResult detailing total processed, indexed, and tokens counted.
     */
    CorpusSyncResult rebuildIndex();

    /**
     * Loads existing indexed terms, postings, and document metadata from persistent storage
     * into the in-memory inverted index for fast in-memory querying.
     */
    void warmupInMemoryIndex();

    /**
     * Retrieves current corpus-wide indexing statistics.
     *
     * @return IndexingStats record.
     */
    IndexingStats getStats();
}
