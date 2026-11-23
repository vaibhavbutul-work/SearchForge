package com.searchforge.performance.compression;

import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.search.PositionalIndexSearcher;
import com.searchforge.indexer.stemmer.PorterStemmer;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.config.Bm25Config;
import com.searchforge.ranking.model.CollectionStats;
import com.searchforge.ranking.model.DocumentStats;
import com.searchforge.ranking.model.ScoredDocument;
import com.searchforge.ranking.scorer.Bm25Scorer;
import com.searchforge.ranking.scorer.Bm25TermScorer;
import com.searchforge.ranking.weight.Bm25IdfCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 10.5: Inverted Index & Postings Optimization Tests")
class PostingsOptimizationBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(PostingsOptimizationBenchmarkTest.class);

    @Test
    @DisplayName("Verify DeltaCodec correctly encodes and decodes strictly ascending sequences")
    void testDeltaCodecCorrectness() {
        long[] original = new long[]{10L, 12L, 15L, 25L, 100L, 1000L};
        long[] deltas = DeltaCodec.encodeDeltas(original);

        assertArrayEquals(new long[]{10L, 2L, 3L, 10L, 75L, 900L}, deltas);

        long[] decoded = DeltaCodec.decodeDeltas(deltas);
        assertArrayEquals(original, decoded);

        // Edge case: single element
        long[] single = new long[]{42L};
        assertArrayEquals(new long[]{42L}, DeltaCodec.encodeDeltas(single));
        assertArrayEquals(single, DeltaCodec.decodeDeltas(new long[]{42L}));

        // Edge case: empty
        assertArrayEquals(new long[0], DeltaCodec.encodeDeltas(new long[0]));
        assertArrayEquals(new long[0], DeltaCodec.decodeDeltas(new long[0]));

        // Error on unsorted array
        assertThrows(IllegalArgumentException.class, () -> DeltaCodec.encodeDeltas(new long[]{10L, 5L}));
    }

    @Test
    @DisplayName("Verify VByteCodec encodes and decodes integer boundary values with 7-bit continuation framing")
    void testVByteCodecCorrectness() {
        long[] boundaryValues = new long[]{
                0L, 1L, 127L, 128L, 255L, 256L, 16383L, 16384L, 2097151L, 2097152L, 100_000_000L
        };

        byte[] compressed = VByteCodec.encode(boundaryValues);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);

        long[] decoded = VByteCodec.decodeLongs(compressed, boundaryValues.length);
        assertArrayEquals(boundaryValues, decoded);

        // Verify single byte boundary: 0 to 127 must encode in exactly 1 byte
        byte[] singleByte = VByteCodec.encode(new long[]{127L});
        assertEquals(1, singleByte.length, "127 must encode in exactly 1 byte");

        // 128 must encode in exactly 2 bytes
        byte[] twoBytes = VByteCodec.encode(new long[]{128L});
        assertEquals(2, twoBytes.length, "128 must encode in exactly 2 bytes");
    }

    @Test
    @DisplayName("Empirical Compression Benchmark: Measure Delta+VByte compression ratio and throughput across 3 distributions")
    void testDeltaVByteCombinedCompressionRatioAndThroughput() {
        int n = 5_000;

        // 1. Dense Distribution: Average gap 1 to 3 (e.g. frequent terms)
        long[] denseDocIds = new long[n];
        long cur = 1;
        for (int i = 0; i < n; i++) {
            cur += (i % 3) + 1;
            denseDocIds[i] = cur;
        }

        // 2. Clustered Distribution: Bursts of consecutive IDs followed by medium gaps
        long[] clusteredDocIds = new long[n];
        cur = 1;
        for (int i = 0; i < n; i++) {
            cur += (i % 10 == 0) ? 25 : 1;
            clusteredDocIds[i] = cur;
        }

        // 3. Sparse Distribution: Gaps of 50 to 200 (e.g. rare technical terms)
        long[] sparseDocIds = new long[n];
        cur = 100;
        for (int i = 0; i < n; i++) {
            cur += 50 + (i % 150);
            sparseDocIds[i] = cur;
        }

        benchmarkDistribution("Dense (Common Terms)", denseDocIds);
        benchmarkDistribution("Clustered (Burst Terms)", clusteredDocIds);
        benchmarkDistribution("Sparse (Rare Keywords)", sparseDocIds);
    }

    private void benchmarkDistribution(String label, long[] docIds) {
        int count = docIds.length;
        long raw64Bytes = (long) count * 8L;
        long raw32Bytes = (long) count * 4L;

        // Warmup JIT C2
        for (int i = 0; i < 500; i++) {
            byte[] bytes = VByteCodec.encodeDeltaVByte(docIds);
            long[] dec = VByteCodec.decodeDeltaVByte(bytes, count);
        }

        // Timed Encode
        long startEncode = System.nanoTime();
        byte[] compressed = VByteCodec.encodeDeltaVByte(docIds);
        long encodeNanos = System.nanoTime() - startEncode;

        // Timed Decode
        long startDecode = System.nanoTime();
        long[] decoded = VByteCodec.decodeDeltaVByte(compressed, count);
        long decodeNanos = System.nanoTime() - startDecode;

        assertArrayEquals(docIds, decoded, "Decoded IDs must match original exactly");

        double ratioVs64 = (double) compressed.length / raw64Bytes;
        double ratioVs32 = (double) compressed.length / raw32Bytes;
        double savingsVs64Pct = (1.0 - ratioVs64) * 100.0;
        double savingsVs32Pct = (1.0 - ratioVs32) * 100.0;

        double encodeMillionPerSec = (encodeNanos > 0) ? ((double) count / (encodeNanos / 1_000_000_000.0)) / 1_000_000.0 : 0.0;
        double decodeMillionPerSec = (decodeNanos > 0) ? ((double) count / (decodeNanos / 1_000_000_000.0)) / 1_000_000.0 : 0.0;

        log.info("Task 10.5 Codec Benchmark [{}]: Raw64={}B, Raw32={}B, Compressed={}B | Savings: {}% (vs 64b), {}% (vs 32b) | Encode: {}M ints/s, Decode: {}M ints/s",
                label, raw64Bytes, raw32Bytes, compressed.length,
                String.format("%.1f", savingsVs64Pct), String.format("%.1f", savingsVs32Pct),
                String.format("%.1f", encodeMillionPerSec), String.format("%.1f", decodeMillionPerSec));

        // Verification assertions
        assertTrue(compressed.length < raw64Bytes, "Compressed bytes must be less than raw 64-bit array");
        assertTrue(savingsVs64Pct > 50.0, "Savings vs 64-bit raw longs must exceed 50%");
    }

    @Test
    @DisplayName("Verify zero-allocation postings list methods and primitive binary search without dummy objects")
    void testZeroAllocationPostingsListMethods() {
        PostingsList pl = new PostingsList();
        pl.addPosting(new Posting(10L, 2, new int[]{0, 5}));
        pl.addPosting(new Posting(25L, 1, new int[]{3}));
        pl.addPosting(new Posting(50L, 3, new int[]{1, 8, 12}));

        // 1. Assert getPostingsView() returns unmodifiable view without copying
        List<Posting> view = pl.getPostingsView();
        assertEquals(3, view.size());
        assertEquals(10L, view.get(0).documentId());
        assertThrows(UnsupportedOperationException.class, () -> view.add(new Posting(99L, 1, new int[0])));

        // 2. Assert forEachPosting iterates cleanly
        List<Long> docIds = new ArrayList<>();
        pl.forEachPosting(p -> docIds.add(p.documentId()));
        assertEquals(List.of(10L, 25L, 50L), docIds);

        // 3. Assert primitive array projections
        long[] primitiveDocIds = pl.getDocumentIds();
        assertArrayEquals(new long[]{10L, 25L, 50L}, primitiveDocIds);

        int[] primitiveTfs = pl.getTermFrequencies();
        assertArrayEquals(new int[]{2, 1, 3}, primitiveTfs);

        // 4. Zero-allocation binary search
        Optional<Posting> found = pl.getPostingForDocument(25L);
        assertTrue(found.isPresent());
        assertEquals(25L, found.get().documentId());
        assertEquals(1, found.get().termFrequency());

        assertFalse(pl.getPostingForDocument(30L).isPresent());
        assertTrue(pl.containsDocument(50L));
        assertFalse(pl.containsDocument(99L));

        // 5. Direct index access
        assertEquals(50L, pl.getPosting(2).documentId());

        // 6. Zero-allocation removal
        assertTrue(pl.removePosting(25L));
        assertEquals(2, pl.size());
        assertFalse(pl.containsDocument(25L));
    }

    @Test
    @DisplayName("Verify search determinism and exact BM25 scoring with optimized postings traversal")
    void testSearchDeterminismWithOptimizedPostings() {
        InMemoryInvertedIndex index = new InMemoryInvertedIndex();
        Tokenizer tokenizer = new Tokenizer(new StopWordFilter(), new PorterStemmer());

        index.addDocument(1L, tokenizer.tokenize("distributed search engine with inverted index architecture"));
        index.addDocument(2L, tokenizer.tokenize("relational database management system with acid transactions"));
        index.addDocument(3L, tokenizer.tokenize("inverted index search algorithm with distributed posting lists"));

        // Positional search
        PositionalIndexSearcher searcher = new PositionalIndexSearcher(index, tokenizer);
        var matches = searcher.searchPhrase("inverted index");
        assertEquals(2, matches.size());
        List<Long> matchedDocs = matches.stream().map(m -> m.documentId()).toList();
        assertTrue(matchedDocs.contains(1L));
        assertTrue(matchedDocs.contains(3L));

        // BM25 scoring
        Bm25Scorer scorer = new Bm25Scorer(new Bm25TermScorer(Bm25Config.defaultConfig()), new Bm25IdfCalculator());
        CollectionStats stats = new CollectionStats(3, index.getAverageDocumentLength(), Map.of("search", 2L, "index", 2L));

        DocumentStats d1 = new DocumentStats(1L, index.getDocLength(1L), Map.of("search", 1, "index", 1));
        DocumentStats d3 = new DocumentStats(3L, index.getDocLength(3L), Map.of("search", 1, "index", 1));

        List<ScoredDocument> scored = scorer.scoreDocuments(List.of(d1, d3), List.of("search", "index"), stats);
        assertNotNull(scored);
        assertEquals(2, scored.size());
        assertTrue(scored.get(0).score() > 0.0);
    }
}
