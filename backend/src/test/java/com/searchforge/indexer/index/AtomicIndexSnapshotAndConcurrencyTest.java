package com.searchforge.indexer.index;

import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.token.ProcessedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 6: Atomic In-Memory Index Snapshotting & Read-Isolated Postings Replacement Tests")
class AtomicIndexSnapshotAndConcurrencyTest {

    private InMemoryInvertedIndex index;

    @BeforeEach
    void setUp() {
        index = new InMemoryInvertedIndex();
    }

    @Test
    @DisplayName("Verify atomic differential document replacement removes obsolete terms and updates positions cleanly")
    void testAtomicDifferentialReplacement() {
        long docId = 1L;
        List<ProcessedToken> initialTokens = List.of(
                new ProcessedToken("java", 0),
                new ProcessedToken("search", 1),
                new ProcessedToken("engine", 2)
        );

        index.addDocument(docId, initialTokens);

        assertEquals(1, index.getTotalDocuments());
        assertEquals(3, index.getDocLength(docId));
        assertEquals(3.0, index.getAverageDocumentLength());
        assertEquals(3, index.getVocabularySize());

        // Replace document with new token stream: "search" at position 0, "concurrency" at position 1
        // "java" and "engine" are obsolete and should be purged
        List<ProcessedToken> updatedTokens = List.of(
                new ProcessedToken("search", 0),
                new ProcessedToken("concurrency", 1)
        );

        index.replaceDocument(docId, updatedTokens);

        assertEquals(1, index.getTotalDocuments(), "Total documents must remain 1");
        assertEquals(2, index.getDocLength(docId), "Document length must update to 2");
        assertEquals(2.0, index.getAverageDocumentLength(), "Average document length must update to 2.0");
        assertEquals(2, index.getVocabularySize(), "Vocabulary size must be 2 ('search' and 'concurrency')");

        // Obsolete terms must be purged
        assertTrue(index.getPostings("java").isEmpty(), "'java' posting must be purged");
        assertTrue(index.getPostings("engine").isEmpty(), "'engine' posting must be purged");

        // Retained and new terms must be accurately populated
        Optional<PostingsList> searchPostingsOpt = index.getPostings("search");
        assertTrue(searchPostingsOpt.isPresent());
        List<Posting> searchPostings = searchPostingsOpt.get().getPostings();
        assertEquals(1, searchPostings.size());
        assertEquals(1, searchPostings.get(0).termFrequency());
        assertEquals(List.of(0), searchPostings.get(0).positions(), "Position must be replaced by [0], not merged");

        Optional<PostingsList> concurrencyPostingsOpt = index.getPostings("concurrency");
        assertTrue(concurrencyPostingsOpt.isPresent());
        List<Posting> concurrencyPostings = concurrencyPostingsOpt.get().getPostings();
        assertEquals(1, concurrencyPostings.size());
        assertEquals(List.of(1), concurrencyPostings.get(0).positions());
    }

    @Test
    @DisplayName("Verify point-in-time IndexQuerySnapshot guarantees read isolation during active background deletions and updates")
    void testReadIsolatedSnapshotIntegrityDuringConcurrentDeletion() {
        index.addDocument(1L, List.of(
                new ProcessedToken("distributed", 0),
                new ProcessedToken("systems", 1)
        ));
        index.addDocument(2L, List.of(
                new ProcessedToken("distributed", 0),
                new ProcessedToken("database", 1)
        ));

        assertEquals(2, index.getTotalDocuments());

        // Capture snapshot before mutations
        IndexQuerySnapshot snapshot = index.getSnapshot(List.of("distributed", "database"));

        assertNotNull(snapshot);
        assertEquals(2, snapshot.totalDocuments());
        assertEquals(2.0, snapshot.averageDocumentLength());
        assertEquals(2, snapshot.getDocLength(1L));
        assertEquals(2, snapshot.getDocLength(2L));

        // Background write operations: delete doc 1 and replace doc 2 with single token
        index.removeDocument(1L);
        index.replaceDocument(2L, List.of(new ProcessedToken("database", 0)));

        // Live index reflects mutations
        assertEquals(1, index.getTotalDocuments());
        assertEquals(0, index.getDocLength(1L));
        assertEquals(1, index.getDocLength(2L));
        assertTrue(index.getPostings("distributed").isEmpty());

        // Snapshot MUST remain completely isolated and intact
        assertEquals(2, snapshot.totalDocuments(), "Snapshot total documents must remain frozen at 2");
        assertEquals(2.0, snapshot.averageDocumentLength(), "Snapshot average doc length must remain frozen at 2.0");
        assertEquals(2, snapshot.getDocLength(1L), "Snapshot doc 1 length must remain frozen at 2");
        assertEquals(2, snapshot.getDocLength(2L), "Snapshot doc 2 length must remain frozen at 2");

        Optional<PostingsList> snapshotDistributed = snapshot.getPostings("distributed");
        assertTrue(snapshotDistributed.isPresent(), "Snapshot must retain 'distributed' postings");
        assertEquals(2, snapshotDistributed.get().size(), "Snapshot must still see 2 documents in 'distributed'");
    }

    @Test
    @DisplayName("Verify high-frequency concurrent read and write operations never observe dl=0 or corrupted stats")
    void testHighFrequencyConcurrentReadWriteIsolation() throws Exception {
        int docCount = 30;
        for (long id = 1; id <= docCount; id++) {
            index.addDocument(id, List.of(
                    new ProcessedToken("common", 0),
                    new ProcessedToken("term_" + (id % 5), 1),
                    new ProcessedToken("doc_" + id, 2)
            ));
        }

        int readerThreads = 8;
        int writerThreads = 6;
        int iterationsPerReader = 300;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(readerThreads + writerThreads);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> failureRef = new AtomicReference<>();
        AtomicInteger successfulReads = new AtomicInteger(0);

        // Writers continuously mutating and replacing documents
        for (int w = 0; w < writerThreads; w++) {
            final int writerId = w;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int counter = 0;
                    while (running.get()) {
                        long docId = (counter % docCount) + 1;
                        if (counter % 2 == 0) {
                            index.replaceDocument(docId, List.of(
                                    new ProcessedToken("common", 0),
                                    new ProcessedToken("updated_" + writerId, 1),
                                    new ProcessedToken("term_" + (counter % 5), 2),
                                    new ProcessedToken("token_" + counter, 3)
                            ));
                        } else {
                            index.replaceDocument(docId, List.of(
                                    new ProcessedToken("common", 0),
                                    new ProcessedToken("term_" + (counter % 5), 1)
                            ));
                        }
                        counter++;
                        Thread.yield();
                    }
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Readers executing atomic snapshots and checking strict invariant guarantees
        for (int r = 0; r < readerThreads; r++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterationsPerReader; i++) {
                        IndexQuerySnapshot snapshot = index.getSnapshot(List.of("common", "updated_0", "term_1"));

                        assertTrue(snapshot.totalDocuments() > 0, "Total documents in snapshot must be > 0");
                        assertTrue(snapshot.averageDocumentLength() > 0.0, "Average document length must be > 0");

                        Optional<PostingsList> commonOpt = snapshot.getPostings("common");
                        if (commonOpt.isPresent()) {
                            List<Posting> postings = commonOpt.get().getPostings();
                            for (Posting p : postings) {
                                int dl = snapshot.getDocLength(p.documentId());
                                // INVARIANT: If a document appears in postings, its length in the snapshot
                                // MUST NEVER be 0 or negative, even with continuous concurrent replacements!
                                assertTrue(dl > 0, "Document " + p.documentId() + " in postings must have dl > 0, but was " + dl);
                                assertEquals(p.positions().size(), p.termFrequency(), "TF must match positions length");
                            }
                        }
                        successfulReads.incrementAndGet();
                    }
                } catch (Throwable t) {
                    failureRef.compareAndSet(null, t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();

        // Wait for readers to finish their iterations
        boolean readersCompleted = false;
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (successfulReads.get() >= readerThreads * iterationsPerReader) {
                readersCompleted = true;
                break;
            }
            Thread.sleep(20);
        }

        running.set(false);
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        if (failureRef.get() != null) {
            fail("Concurrent index reading and writing test failed with assertion violation", failureRef.get());
        }

        assertTrue(readersCompleted, "All reader threads must complete their target iterations successfully");
        assertEquals(readerThreads * iterationsPerReader, successfulReads.get());
    }

    @Test
    @DisplayName("Verify replacing document with empty or null tokens cleanly delegates to document removal")
    void testEmptyDocumentReplacementPrunesDoc() {
        index.addDocument(1L, List.of(new ProcessedToken("hello", 0), new ProcessedToken("world", 1)));
        assertEquals(1, index.getTotalDocuments());
        assertEquals(2, index.getDocLength(1L));

        // Replace with empty list
        index.replaceDocument(1L, List.of());

        assertEquals(0, index.getTotalDocuments(), "Total documents must be 0");
        assertEquals(0, index.getDocLength(1L), "Doc length must be 0");
        assertTrue(index.getPostings("hello").isEmpty(), "'hello' must be pruned");
        assertTrue(index.getPostings("world").isEmpty(), "'world' must be pruned");
    }
}
