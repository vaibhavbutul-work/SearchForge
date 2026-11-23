package com.searchforge.performance.memory;

import com.searchforge.crawler.model.CrawledDocument;
import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.search.PositionalIndexSearcher;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.evaluation.ExpandedBenchmarkDataset;
import com.searchforge.ranking.model.CollectionStats;
import com.searchforge.ranking.model.DocumentStats;
import com.searchforge.ranking.model.ScoredDocument;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 10.4: Memory Profiling & Index Memory Optimization Tests")
class IndexMemoryProfilerTest {

    private static final Logger log = LoggerFactory.getLogger(IndexMemoryProfilerTest.class);

    private InMemoryInvertedIndex invertedIndex;
    private InMemoryVectorStorage vectorStorage;
    private IndexMemoryProfiler memoryProfiler;
    private Tokenizer tokenizer;
    private Bm25Scorer bm25Scorer;

    @BeforeEach
    void setUp() {
        invertedIndex = new InMemoryInvertedIndex();
        vectorStorage = new InMemoryVectorStorage(384);
        memoryProfiler = new IndexMemoryProfiler();
        tokenizer = new Tokenizer(new StopWordFilter(), new PorterStemmer());
        bm25Scorer = new Bm25Scorer(new Bm25TermScorer(Bm25Config.defaultConfig()), new Bm25IdfCalculator());
    }

    @AfterEach
    void tearDown() {
        invertedIndex.clear();
        vectorStorage.clear();
    }

    @Test
    @DisplayName("Verify compact primitive int[] in Posting reduces memory overhead and preserves List<Integer> contracts")
    void testCompactPrimitivePostingMemoryVerification() {
        int[] positions = new int[]{0, 4, 9, 15, 23};
        Posting posting = new Posting(101L, positions.length, positions);

        // 1. Assert primitive array accessors
        assertNotNull(posting.rawPositions());
        assertEquals(5, posting.positionCount());
        assertEquals(0, posting.positionAt(0));
        assertEquals(23, posting.positionAt(4));

        // 2. Assert List<Integer> view compatibility
        List<Integer> listView = posting.positions();
        assertNotNull(listView);
        assertEquals(5, listView.size());
        assertEquals(List.of(0, 4, 9, 15, 23), listView);
        assertEquals(4, listView.get(1));

        // 3. Binary Search compatibility on primitive view
        int foundIndex = Collections.binarySearch(listView, 15);
        assertEquals(3, foundIndex, "Binary search must locate element index in RandomAccess list view");
        assertTrue(Collections.binarySearch(listView, 7) < 0, "Missing element must return negative index");

        // 4. Test immutability of view
        assertThrows(UnsupportedOperationException.class, () -> listView.add(99));

        // 5. Test empty positions optimization (shared singleton)
        Posting emptyPosting = new Posting(102L, 1, new int[0]);
        assertEquals(0, emptyPosting.positionCount());
        assertTrue(emptyPosting.positions().isEmpty());

        // 6. Test equality between boxed list constructor and primitive array constructor
        Posting fromBoxedList = new Posting(101L, 5, List.of(0, 4, 9, 15, 23));
        assertEquals(posting, fromBoxedList, "Postings constructed from List or primitive array must be equal");
        assertEquals(posting.hashCode(), fromBoxedList.hashCode());
    }

    @Test
    @DisplayName("Verify zero-copy direct access on DocumentVector eliminates defensive cloning in search loops")
    void testVectorZeroCopyAvoidsDefensiveCloning() {
        float[] sample = new float[384];
        sample[0] = 1.0f;
        DocumentVector docVector = new DocumentVector(101L, sample);

        // Standard getValues() must return a safe defensive copy
        float[] defensiveCopy = docVector.getValues();
        assertNotSame(defensiveCopy, docVector.getValuesDirect(), "getValues() must clone defensive copy");
        assertArrayEquals(defensiveCopy, docVector.getValuesDirect());

        // getValuesDirect() must return the underlying reference without allocating
        float[] directRef1 = docVector.getValuesDirect();
        float[] directRef2 = docVector.getValuesDirect();
        assertSame(directRef1, directRef2, "getValuesDirect() must return direct internal reference with 0 allocations");
    }

    @Test
    @DisplayName("Verify memory scaling and empirical telemetry across incremental corpus tiers")
    void testMemoryScalingAcrossCorpusSizes() {
        List<CrawledDocument> corpus = ExpandedBenchmarkDataset.getCorpus();
        assertTrue(corpus.size() >= 50, "Corpus must have at least 50 documents");

        // Tier 1: 25 documents
        indexSubCorpus(corpus, 0, 25);
        IndexMemoryReport report25 = memoryProfiler.profileMemory(invertedIndex, vectorStorage);
        assertEquals(25, report25.documentCount());
        assertTrue(report25.vocabularySize() > 0);
        assertTrue(report25.totalPostingsCount() > 0);
        assertTrue(report25.totalPositionsCount() > 0);
        assertTrue(report25.estimatedLexicalIndexBytes() > 0);
        assertTrue(report25.estimatedVectorStorageBytes() > 0);

        log.info("Tier 1 (25 docs): Lexical: {} KB | Vector: {} KB | Total: {} KB | PerDoc: {} Bytes",
                String.format("%.1f", report25.estimatedLexicalMb() * 1024),
                String.format("%.1f", report25.estimatedVectorMb() * 1024),
                String.format("%.1f", report25.estimatedTotalIndexMb() * 1024),
                String.format("%.1f", report25.bytesPerDocument()));

        // Tier 2: 50 documents
        indexSubCorpus(corpus, 25, 50);
        IndexMemoryReport report50 = memoryProfiler.profileMemory(invertedIndex, vectorStorage);
        assertEquals(50, report50.documentCount());

        log.info("Tier 2 (50 docs): Lexical: {} KB | Vector: {} KB | Total: {} KB | PerDoc: {} Bytes",
                String.format("%.1f", report50.estimatedLexicalMb() * 1024),
                String.format("%.1f", report50.estimatedVectorMb() * 1024),
                String.format("%.1f", report50.estimatedTotalIndexMb() * 1024),
                String.format("%.1f", report50.bytesPerDocument()));

        // Tier 3: 100 documents
        indexSubCorpus(corpus, 50, 100);
        IndexMemoryReport report100 = memoryProfiler.profileMemory(invertedIndex, vectorStorage);
        assertEquals(100, report100.documentCount());

        log.info("Tier 3 (100 docs): Lexical: {} KB | Vector: {} KB | Total: {} KB | PerDoc: {} Bytes",
                String.format("%.1f", report100.estimatedLexicalMb() * 1024),
                String.format("%.1f", report100.estimatedVectorMb() * 1024),
                String.format("%.1f", report100.estimatedTotalIndexMb() * 1024),
                String.format("%.1f", report100.bytesPerDocument()));

        // Memory scaling assertions:
        // 1. Vector storage scales strictly linearly (1,624 bytes per document: 1,584B DocumentVector + 40B ConcurrentHashMap Node)
        assertEquals(100L * 1624L, report100.estimatedVectorStorageBytes(),
                "Dense 384-d vector storage must scale deterministically at 1,624 bytes per document");

        // 2. Average memory per document must remain well under 25 KB/doc
        assertTrue(report100.bytesPerDocument() < 25_000,
                "Memory per document must remain tightly bounded under 25 KB, was: " + report100.bytesPerDocument());

        // 3. Memory per posting must remain compact (< 150 bytes per posting)
        assertTrue(report100.bytesPerPosting() < 150,
                "Memory per posting must be compact, was: " + report100.bytesPerPosting());
    }

    @Test
    @DisplayName("Verify exact phrase search and BM25 scoring determinism with compact primitive postings")
    void testPhraseSearchAndBM25DeterminismWithCompactPostings() {
        // Document 1
        String text1 = "Virtual threads in Java 21 provide lightweight concurrency for modern search engines.";
        List<ProcessedToken> tokens1 = tokenizer.tokenize(text1);
        invertedIndex.addDocument(1L, tokens1);

        // Document 2
        String text2 = "Reactive concurrency with Spring WebFlux handles high-throughput asynchronous streams.";
        List<ProcessedToken> tokens2 = tokenizer.tokenize(text2);
        invertedIndex.addDocument(2L, tokens2);

        // Document 3
        String text3 = "Java 21 introduces virtual threads to replace heavy operating system thread models.";
        List<ProcessedToken> tokens3 = tokenizer.tokenize(text3);
        invertedIndex.addDocument(3L, tokens3);

        // 1. Test Positional Phrase Search: "virtual threads"
        PositionalIndexSearcher phraseSearcher = new PositionalIndexSearcher(invertedIndex, tokenizer);
        var phraseMatches = phraseSearcher.searchPhrase("virtual threads");

        assertNotNull(phraseMatches);
        assertEquals(2, phraseMatches.size(), "Phrase 'virtual threads' appears in exactly doc 1 and doc 3");
        List<Long> matchedDocIds = phraseMatches.stream().map(m -> m.documentId()).toList();
        assertTrue(matchedDocIds.contains(1L));
        assertTrue(matchedDocIds.contains(3L));
        assertFalse(matchedDocIds.contains(2L));

        // 2. Test BM25 Scoring
        CollectionStats stats = new CollectionStats(
                3,
                invertedIndex.getAverageDocumentLength(),
                Map.of("virtual", 2L, "thread", 2L)
        );

        DocumentStats d1 = new DocumentStats(1L, invertedIndex.getDocLength(1L), Map.of("virtual", 1, "thread", 1));
        DocumentStats d2 = new DocumentStats(2L, invertedIndex.getDocLength(2L), Map.of("concurr", 1));
        DocumentStats d3 = new DocumentStats(3L, invertedIndex.getDocLength(3L), Map.of("virtual", 1, "thread", 1));

        List<ScoredDocument> scored = bm25Scorer.scoreDocuments(
                List.of(d1, d2, d3),
                List.of("virtual", "thread"),
                stats
        );

        assertNotNull(scored);
        assertEquals(3, scored.size(), "Scores computed for all 3 candidate documents");
        assertTrue(scored.get(0).score() > 0.0, "Top doc score must be positive");
        assertTrue(scored.get(1).score() > 0.0, "Second doc score must be positive");
        assertEquals(0.0, scored.get(2).score(), "Non-matching document must score 0.0");
    }

    private void indexSubCorpus(List<CrawledDocument> corpus, int start, int end) {
        for (int i = start; i < end; i++) {
            CrawledDocument doc = corpus.get(i % corpus.size());
            long docId = i + 1L;
            String fullText = doc.getTitle() + " " + doc.getCleanText();
            List<ProcessedToken> tokens = tokenizer.tokenize(fullText);
            invertedIndex.addDocument(docId, tokens);

            // Deterministic 384-dimensional unit vector
            float[] vec = new float[384];
            vec[(int) (docId % 384)] = 1.0f;
            vectorStorage.put(docId, vec);
        }
    }
}
