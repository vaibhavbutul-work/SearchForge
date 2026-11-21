package com.searchforge.lifecycle.metrics;

import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.vector.VectorStorage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe operational metrics collector tracking lifecycle events,
 * state distribution, throughput counters, and staged execution durations.
 */
@Service
public class LifecycleMetricsCollector {

    private final CrawledDocumentRepository crawledDocumentRepository;
    private final VectorStorage vectorStorage;
    private final InvertedIndex inMemoryIndex;

    private final AtomicLong documentsDiscovered = new AtomicLong(0);
    private final AtomicLong documentsFetched = new AtomicLong(0);
    private final AtomicLong documentsProcessed = new AtomicLong(0);
    private final AtomicLong documentsUnchanged = new AtomicLong(0);
    private final AtomicLong documentsUpdated = new AtomicLong(0);
    private final AtomicLong documentsDeleted = new AtomicLong(0);
    private final AtomicLong documentsFailed = new AtomicLong(0);
    private final AtomicLong documentsRetried = new AtomicLong(0);
    private final AtomicLong documentsRepaired = new AtomicLong(0);

    private final LongAdder totalLexicalMs = new LongAdder();
    private final LongAdder totalEmbeddingMs = new LongAdder();
    private final LongAdder totalCommitMs = new LongAdder();
    private final LongAdder totalStageMs = new LongAdder();
    private final AtomicLong stagedOperationCount = new AtomicLong(0);

    public LifecycleMetricsCollector(
            CrawledDocumentRepository crawledDocumentRepository,
            VectorStorage vectorStorage,
            InvertedIndex inMemoryIndex
    ) {
        this.crawledDocumentRepository = Objects.requireNonNull(crawledDocumentRepository);
        this.vectorStorage = Objects.requireNonNull(vectorStorage);
        this.inMemoryIndex = Objects.requireNonNull(inMemoryIndex);
    }

    public void recordDiscovered() {
        documentsDiscovered.incrementAndGet();
    }

    public void recordFetched() {
        documentsFetched.incrementAndGet();
    }

    public void recordProcessed() {
        documentsProcessed.incrementAndGet();
    }

    public void recordUnchanged() {
        documentsUnchanged.incrementAndGet();
    }

    public void recordUpdated(long lexicalMs, long embeddingMs, long commitMs, long totalMs) {
        documentsUpdated.incrementAndGet();
        totalLexicalMs.add(lexicalMs);
        totalEmbeddingMs.add(embeddingMs);
        totalCommitMs.add(commitMs);
        totalStageMs.add(totalMs);
        stagedOperationCount.incrementAndGet();
    }

    public void recordDeleted() {
        documentsDeleted.incrementAndGet();
    }

    public void recordFailed() {
        documentsFailed.incrementAndGet();
    }

    public void recordRetried() {
        documentsRetried.incrementAndGet();
    }

    public void recordRepaired() {
        documentsRepaired.incrementAndGet();
    }

    /**
     * Resets cumulative counters (useful for isolated benchmark runs or test setups).
     */
    public void reset() {
        documentsDiscovered.set(0);
        documentsFetched.set(0);
        documentsProcessed.set(0);
        documentsUnchanged.set(0);
        documentsUpdated.set(0);
        documentsDeleted.set(0);
        documentsFailed.set(0);
        documentsRetried.set(0);
        documentsRepaired.set(0);
        totalLexicalMs.reset();
        totalEmbeddingMs.reset();
        totalCommitMs.reset();
        totalStageMs.reset();
        stagedOperationCount.set(0);
    }

    /**
     * Captures a point-in-time snapshot of system lifecycle metrics.
     */
    public LifecycleSnapshot getSnapshot() {
        Map<DocumentLifecycleStatus, Long> counts = new EnumMap<>(DocumentLifecycleStatus.class);
        for (DocumentLifecycleStatus status : DocumentLifecycleStatus.values()) {
            counts.put(status, crawledDocumentRepository.countByStatus(status));
        }

        long ops = stagedOperationCount.get();
        double avgLexical = ops > 0 ? (double) totalLexicalMs.sum() / ops : 0.0;
        double avgEmbedding = ops > 0 ? (double) totalEmbeddingMs.sum() / ops : 0.0;
        double avgCommit = ops > 0 ? (double) totalCommitMs.sum() / ops : 0.0;
        double avgTotal = ops > 0 ? (double) totalStageMs.sum() / ops : 0.0;

        int activeDocs = counts.getOrDefault(DocumentLifecycleStatus.ACTIVE, 0L).intValue();
        int vectorSize = vectorStorage.size();
        int vocabSize = inMemoryIndex.getVocabularySize();

        return new LifecycleSnapshot(
                Instant.now(),
                counts,
                documentsDiscovered.get(),
                documentsFetched.get(),
                documentsProcessed.get(),
                documentsUnchanged.get(),
                documentsUpdated.get(),
                documentsDeleted.get(),
                documentsFailed.get(),
                documentsRetried.get(),
                documentsRepaired.get(),
                avgLexical,
                avgEmbedding,
                avgCommit,
                avgTotal,
                activeDocs,
                vectorSize,
                vocabSize
        );
    }
}
