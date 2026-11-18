package com.searchforge.indexer.index;

import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.token.ProcessedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryInvertedIndexTest {

    private InMemoryInvertedIndex index;

    @BeforeEach
    void setUp() {
        index = new InMemoryInvertedIndex();
    }

    @Test
    @DisplayName("Should index single document and compute accurate term frequencies and positions")
    void shouldIndexSingleDocument() {
        // Doc 1: "crawl web crawl"
        List<ProcessedToken> tokens = List.of(
                new ProcessedToken("crawl", 0),
                new ProcessedToken("web", 1),
                new ProcessedToken("crawl", 2)
        );

        index.addDocument(1L, tokens);

        assertEquals(1, index.getTotalDocuments());
        assertEquals(3, index.getDocLength(1L));
        assertEquals(3.0, index.getAverageDocumentLength());
        assertEquals(2, index.getVocabularySize());

        // Check "crawl" postings
        Optional<PostingsList> crawlPostings = index.getPostings("crawl");
        assertTrue(crawlPostings.isPresent());
        assertEquals(1, crawlPostings.get().size());
        assertEquals(2L, crawlPostings.get().getTotalTermFrequency());

        Posting p1 = crawlPostings.get().getPostings().get(0);
        assertEquals(1L, p1.documentId());
        assertEquals(2, p1.termFrequency());
        assertEquals(List.of(0, 2), p1.positions());

        // Check "web" postings
        Optional<PostingsList> webPostings = index.getPostings("web");
        assertTrue(webPostings.isPresent());
        assertEquals(1, webPostings.get().size());
        assertEquals(1L, webPostings.get().getTotalTermFrequency());

        Posting p2 = webPostings.get().getPostings().get(0);
        assertEquals(1L, p2.documentId());
        assertEquals(1, p2.termFrequency());
        assertEquals(List.of(1), p2.positions());
    }

    @Test
    @DisplayName("Should index multiple documents and maintain postings in strictly ascending documentId order")
    void shouldMaintainSortedDocumentIdOrder() {
        // Index out of order: doc 7, then doc 2, then doc 5
        index.addDocument(7L, List.of(new ProcessedToken("java", 0)));
        index.addDocument(2L, List.of(new ProcessedToken("java", 1)));
        index.addDocument(5L, List.of(new ProcessedToken("java", 3)));

        Optional<PostingsList> postingsOpt = index.getPostings("java");
        assertTrue(postingsOpt.isPresent());

        List<Posting> postings = postingsOpt.get().getPostings();
        assertEquals(3, postings.size());

        // Must be sorted strictly ascending: 2, 5, 7
        assertEquals(2L, postings.get(0).documentId());
        assertEquals(5L, postings.get(1).documentId());
        assertEquals(7L, postings.get(2).documentId());
        assertEquals(3, postingsOpt.get().size()); // df = 3
    }

    @Test
    @DisplayName("Should compute corpus metrics (N, avgdl, docLengths) accurately")
    void shouldComputeCorpusMetrics() {
        // Doc 1: 4 tokens
        index.addDocument(1L, List.of(
                new ProcessedToken("spring", 0),
                new ProcessedToken("boot", 1),
                new ProcessedToken("java", 2),
                new ProcessedToken("backend", 3)
        ));

        // Doc 2: 6 tokens
        index.addDocument(2L, List.of(
                new ProcessedToken("search", 0),
                new ProcessedToken("engine", 1),
                new ProcessedToken("inverted", 2),
                new ProcessedToken("index", 3),
                new ProcessedToken("ranking", 4),
                new ProcessedToken("bm25", 5)
        ));

        assertEquals(2, index.getTotalDocuments());
        assertEquals(4, index.getDocLength(1L));
        assertEquals(6, index.getDocLength(2L));
        assertEquals(5.0, index.getAverageDocumentLength(), 0.001); // (4 + 6) / 2 = 5.0
    }

    @Test
    @DisplayName("Should handle empty or null document tokens gracefully")
    void shouldHandleEmptyOrNullTokens() {
        index.addDocument(10L, List.of());
        index.addDocument(20L, null);

        assertEquals(2, index.getTotalDocuments());
        assertEquals(0, index.getDocLength(10L));
        assertEquals(0, index.getDocLength(20L));
        assertEquals(0.0, index.getAverageDocumentLength());
        assertEquals(0, index.getVocabularySize());
    }

    @Test
    @DisplayName("Should support clear() to purge index")
    void shouldSupportClear() {
        index.addDocument(1L, List.of(new ProcessedToken("test", 0)));
        assertEquals(1, index.getTotalDocuments());

        index.clear();
        assertEquals(0, index.getTotalDocuments());
        assertEquals(0, index.getVocabularySize());
        assertEquals(0.0, index.getAverageDocumentLength());
        assertTrue(index.getPostings("test").isEmpty());
    }

    @Test
    @DisplayName("Should support thread-safe concurrent document indexing without race conditions")
    void shouldSupportConcurrentDocumentIndexing() throws InterruptedException {
        int threadCount = 8;
        int docsPerThread = 25;
        int totalExpectedDocs = threadCount * docsPerThread; // 200 documents

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int d = 0; d < docsPerThread; d++) {
                        long docId = threadId * 1000L + d;
                        index.addDocument(docId, List.of(
                                new ProcessedToken("shared", 0),
                                new ProcessedToken("term_" + threadId, 1)
                        ));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(totalExpectedDocs, index.getTotalDocuments());

        // "shared" should be present in all 200 documents
        Optional<PostingsList> sharedPostings = index.getPostings("shared");
        assertTrue(sharedPostings.isPresent());
        assertEquals(totalExpectedDocs, sharedPostings.get().size());
        assertEquals(totalExpectedDocs, sharedPostings.get().getTotalTermFrequency());
    }
}
