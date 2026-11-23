package com.searchforge.performance.memory;

import com.searchforge.indexer.index.InMemoryInvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.model.TermMetadata;
import com.searchforge.semantic.vector.DocumentVector;
import com.searchforge.semantic.vector.InMemoryVectorStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;

/**
 * Memory Profiling and Deep Structural Footprint Analyzer for SearchForge.
 *
 * Measures JVM heap utilization and computes analytical retained memory
 * consumption for both lexical (Inverted Index) and dense (Vector Storage) tiers.
 */
@Component
public class IndexMemoryProfiler {

    private static final Logger log = LoggerFactory.getLogger(IndexMemoryProfiler.class);

    // Standard 64-bit JVM memory layout constants (Compressed OOPs enabled)
    public static final int OBJECT_HEADER_BYTES = 12;
    public static final int ARRAY_HEADER_BYTES = 16;
    public static final int REFERENCE_BYTES = 4;
    public static final int CHASHMAP_NODE_OVERHEAD = 32;

    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();

    /**
     * Executes Garbage Collection and stabilization pauses to obtain quiescent JVM heap stats.
     */
    public void quiesceGarbageCollector() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(30);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Captures current JVM heap memory snapshot.
     */
    public MemoryUsage getHeapUsage() {
        quiesceGarbageCollector();
        return memoryMXBean.getHeapMemoryUsage();
    }

    /**
     * Estimates structural retained memory for an individual Posting instance in bytes.
     */
    public static long estimatePostingMemory(Posting p) {
        if (p == null) return 0L;
        long postingObjectBytes = 32L;
        long rawArrayBytes = ARRAY_HEADER_BYTES + (long) p.positionCount() * 4L;
        long alignedArrayBytes = (rawArrayBytes + 7) & ~7;
        return postingObjectBytes + alignedArrayBytes;
    }

    /**
     * Profiles the in-memory inverted index and vector storage, generating a comprehensive
     * structural and JVM heap memory audit report.
     *
     * @param invertedIndex The active lexical inverted index
     * @param vectorStorage The active dense vector storage
     * @return Fully populated IndexMemoryReport
     */
    public IndexMemoryReport profileMemory(InMemoryInvertedIndex invertedIndex, InMemoryVectorStorage vectorStorage) {
        quiesceGarbageCollector();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

        int docCount = (invertedIndex != null) ? invertedIndex.getDocumentCount() : 0;
        int vocabSize = (invertedIndex != null) ? invertedIndex.getVocabularySize() : 0;
        long totalTokens = (invertedIndex != null) ? invertedIndex.getTotalTokens() : 0;

        long totalPostings = 0;
        long totalPositions = 0;
        long lexicalIndexBytes = 0;

        if (invertedIndex != null) {
            Map<String, TermMetadata> dictMetadata = invertedIndex.getDictionaryMetadata();

            // 1. Dictionary ConcurrentHashMap Overhead:
            // Node overhead + Reference to Term string + Reference to PostingsList
            long dictMapOverhead = (long) vocabSize * (CHASHMAP_NODE_OVERHEAD + 2L * REFERENCE_BYTES);
            lexicalIndexBytes += dictMapOverhead;

            for (Map.Entry<String, TermMetadata> entry : dictMetadata.entrySet()) {
                String term = entry.getKey();
                TermMetadata meta = entry.getValue();

                // Term String memory: 24 bytes String object + byte[] value array (16 header + length)
                long stringBytes = 24L + ARRAY_HEADER_BYTES + term.length();
                lexicalIndexBytes += stringBytes;

                // PostingsList object overhead: Header + List reference + totalTermFrequency (long) + size (int)
                long postingsListOverhead = OBJECT_HEADER_BYTES + 8L + 4L + REFERENCE_BYTES + 24L; // ArrayList wrapper
                lexicalIndexBytes += postingsListOverhead;

                PostingsList pl = invertedIndex.getPostings(term).orElse(null);
                if (pl != null) {
                    int postingsInList = pl.size();
                    totalPostings += postingsInList;

                    // ArrayList internal Object[] array: header + capacity * reference
                    long arrayListArrayOverhead = ARRAY_HEADER_BYTES + (long) postingsInList * REFERENCE_BYTES;
                    lexicalIndexBytes += arrayListArrayOverhead;

                    for (Posting p : pl.getPostings()) {
                        int posCount = p.termFrequency();
                        totalPositions += posCount;

                        // Posting instance overhead:
                        // Header (12B) + docId (8B) + tf (4B) + positions pointer (4B) + padding (4B) = 32 bytes
                        long postingObjectBytes = 32L;

                        // Packed primitive int[] array:
                        // Header (16B) + posCount * 4B (aligned to 8B boundary)
                        long rawArrayBytes = ARRAY_HEADER_BYTES + (long) posCount * 4L;
                        long alignedArrayBytes = (rawArrayBytes + 7) & ~7;

                        lexicalIndexBytes += (postingObjectBytes + alignedArrayBytes);
                    }
                }
            }

            // 2. Document lengths map: docLengths (ConcurrentHashMap<Long, Integer>)
            // docCount * (Node overhead + boxed Long [24B] + boxed Integer [16B])
            long docLengthsOverhead = (long) docCount * (CHASHMAP_NODE_OVERHEAD + 40L);
            lexicalIndexBytes += docLengthsOverhead;

            // 3. Document terms tracking map: docTerms (ConcurrentHashMap<Long, Set<String>>)
            long docTermsOverhead = (long) docCount * (CHASHMAP_NODE_OVERHEAD + 32L);
            lexicalIndexBytes += docTermsOverhead;
        }

        // 4. Vector Storage Structural Footprint:
        long vectorStorageBytes = 0;
        if (vectorStorage != null) {
            int vectorCount = vectorStorage.size();
            int dims = vectorStorage.dimensions();

            // ConcurrentHashMap overhead for vectorMap
            long vectorMapOverhead = (long) vectorCount * (CHASHMAP_NODE_OVERHEAD + 2L * REFERENCE_BYTES);

            // Per DocumentVector:
            // Header (12B) + docId (8B) + dims (4B) + float[] array ref (4B) + padding (4B) = 32B
            // float[dims] array: Array Header (16B) + dims * 4B = 16 + 1536 = 1552B
            // Total per DocumentVector = 32 + 1552 = 1,584 bytes (~1.55 KB)
            long perVectorBytes = 32L + ARRAY_HEADER_BYTES + (long) dims * 4L;
            vectorStorageBytes = vectorMapOverhead + (long) vectorCount * perVectorBytes;
        }

        long totalIndexBytes = lexicalIndexBytes + vectorStorageBytes;
        double bytesPerDoc = (docCount > 0) ? (double) totalIndexBytes / docCount : 0.0;
        double bytesPerPosting = (totalPostings > 0) ? (double) lexicalIndexBytes / totalPostings : 0.0;
        double bytesPerTerm = (vocabSize > 0) ? (double) lexicalIndexBytes / vocabSize : 0.0;

        IndexMemoryReport report = new IndexMemoryReport(
                docCount,
                vocabSize,
                totalPostings,
                totalPositions,
                totalTokens,
                heapUsage.getUsed(),
                heapUsage.getCommitted(),
                heapUsage.getMax(),
                lexicalIndexBytes,
                vectorStorageBytes,
                totalIndexBytes,
                bytesPerDoc,
                bytesPerPosting,
                bytesPerTerm
        );

        log.info("Task 10.4 Memory Profile: {}", report);
        return report;
    }
}
