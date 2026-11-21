package com.searchforge.lifecycle.metrics;

import com.searchforge.crawler.repository.CrawledDocumentRepository;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.lifecycle.model.DocumentLifecycleStatus;
import com.searchforge.semantic.vector.VectorStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Task 10: Lifecycle Observability & Operational Metrics Unit Tests")
class LifecycleMetricsCollectorTest {

    @Mock
    private CrawledDocumentRepository crawledDocumentRepository;

    @Mock
    private VectorStorage vectorStorage;

    @Mock
    private InvertedIndex inMemoryIndex;

    private LifecycleMetricsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new LifecycleMetricsCollector(crawledDocumentRepository, vectorStorage, inMemoryIndex);
    }

    @Test
    @DisplayName("Recording lifecycle events correctly increments respective telemetry counters")
    void testRecordLifecycleCounters() {
        collector.recordDiscovered();
        collector.recordDiscovered();
        collector.recordFetched();
        collector.recordProcessed();
        collector.recordUnchanged();
        collector.recordDeleted();
        collector.recordFailed();
        collector.recordRetried();
        collector.recordRepaired();

        when(crawledDocumentRepository.countByStatus(any(DocumentLifecycleStatus.class))).thenReturn(0L);
        when(vectorStorage.size()).thenReturn(10);
        when(inMemoryIndex.getVocabularySize()).thenReturn(50);

        LifecycleSnapshot snapshot = collector.getSnapshot();

        assertEquals(2, snapshot.documentsDiscovered());
        assertEquals(1, snapshot.documentsFetched());
        assertEquals(1, snapshot.documentsProcessed());
        assertEquals(1, snapshot.documentsUnchanged());
        assertEquals(1, snapshot.documentsDeleted());
        assertEquals(1, snapshot.documentsFailed());
        assertEquals(1, snapshot.documentsRetried());
        assertEquals(1, snapshot.documentsRepaired());
        assertEquals(10, snapshot.vectorStorageSize());
        assertEquals(50, snapshot.vocabularySize());
    }

    @Test
    @DisplayName("Stage timing recording correctly calculates average durations")
    void testStageTimingAverages() {
        collector.recordUpdated(10, 20, 5, 35);
        collector.recordUpdated(20, 40, 15, 75);

        when(crawledDocumentRepository.countByStatus(any(DocumentLifecycleStatus.class))).thenReturn(0L);
        when(vectorStorage.size()).thenReturn(0);
        when(inMemoryIndex.getVocabularySize()).thenReturn(0);

        LifecycleSnapshot snapshot = collector.getSnapshot();

        assertEquals(2, snapshot.documentsUpdated());
        assertEquals(15.0, snapshot.averageLexicalDurationMs(), 0.001);
        assertEquals(30.0, snapshot.averageEmbeddingDurationMs(), 0.001);
        assertEquals(10.0, snapshot.averageCommitDurationMs(), 0.001);
        assertEquals(55.0, snapshot.averageTotalDurationMs(), 0.001);
    }

    @Test
    @DisplayName("Reset method clears all telemetry and timing accumulators")
    void testReset() {
        collector.recordDiscovered();
        collector.recordUpdated(10, 10, 10, 30);
        collector.reset();

        when(crawledDocumentRepository.countByStatus(any(DocumentLifecycleStatus.class))).thenReturn(0L);
        when(vectorStorage.size()).thenReturn(0);
        when(inMemoryIndex.getVocabularySize()).thenReturn(0);

        LifecycleSnapshot snapshot = collector.getSnapshot();

        assertEquals(0, snapshot.documentsDiscovered());
        assertEquals(0, snapshot.documentsUpdated());
        assertEquals(0.0, snapshot.averageTotalDurationMs(), 0.001);
    }
}
