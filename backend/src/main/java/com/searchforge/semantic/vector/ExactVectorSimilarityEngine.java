package com.searchforge.semantic.vector;

import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Exact, baseline implementation of {@link VectorSimilarityEngine}.
 *
 * Scans all stored document vectors using an exact linear dot-product traversal,
 * accumulating candidates in a bounded Min-Heap Priority Queue of size K in O(N * D + N log K) runtime.
 *
 * Guarantees zero false negatives and deterministic tie-breaking.
 */
@Service
public class ExactVectorSimilarityEngine implements VectorSimilarityEngine {

    private final VectorStorage storage;
    private final ActiveDocumentFilter activeDocumentFilter;

    public ExactVectorSimilarityEngine(VectorStorage storage) {
        this(storage, null);
    }

    @Autowired
    public ExactVectorSimilarityEngine(VectorStorage storage,
                                       @Autowired(required = false) ActiveDocumentFilter activeDocumentFilter) {
        this.storage = Objects.requireNonNull(storage, "VectorStorage cannot be null");
        this.activeDocumentFilter = activeDocumentFilter;
    }

    @Override
    public List<VectorSearchResult> search(float[] queryVector, int topK) {
        if (queryVector == null) {
            throw new IllegalArgumentException("Query vector cannot be null");
        }
        if (queryVector.length != storage.dimensions()) {
            throw new IllegalArgumentException(String.format(
                    "Query vector dimension mismatch: expected %d dimensions but got %d",
                    storage.dimensions(), queryVector.length));
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive, got: " + topK);
        }

        if (storage.size() == 0) {
            return Collections.emptyList();
        }

        // Pre-normalize query vector for fast dot product
        float[] normalizedQuery = VectorMath.normalize(queryVector);

        // Min-Heap of size K: lowest similarity candidate sits at the root (peek)
        // If similarities are equal, higher document ID sits at root to be polled out first
        Comparator<VectorSearchResult> minHeapComparator = Comparator
                .comparingDouble(VectorSearchResult::similarityScore)
                .thenComparing((a, b) -> Long.compare(b.documentId(), a.documentId()));

        PriorityQueue<VectorSearchResult> minHeap = new PriorityQueue<>(topK, minHeapComparator);

        storage.forEachVector(candidate -> {
            if (activeDocumentFilter != null && !activeDocumentFilter.isActive(candidate.getDocumentId())) {
                return;
            }
            float similarity = VectorMath.fastNormalizedDotProduct(normalizedQuery, candidate.getValuesDirect());

            VectorSearchResult entry = new VectorSearchResult(candidate.getDocumentId(), similarity, 0);

            if (minHeap.size() < topK) {
                minHeap.offer(entry);
            } else if (minHeapComparator.compare(entry, minHeap.peek()) > 0) {
                minHeap.poll();
                minHeap.offer(entry);
            }
        });

        // Extract elements from min-heap and order descending
        List<VectorSearchResult> rankedList = new ArrayList<>(minHeap.size());
        while (!minHeap.isEmpty()) {
            rankedList.add(minHeap.poll());
        }

        // Sort descending using official ranking comparator
        rankedList.sort(VectorSearchResult.RANKING_COMPARATOR);

        // Assign 1-based ranks
        List<VectorSearchResult> finalResults = new ArrayList<>(rankedList.size());
        for (int i = 0; i < rankedList.size(); i++) {
            finalResults.add(rankedList.get(i).withRank(i + 1));
        }

        return Collections.unmodifiableList(finalResults);
    }
}
