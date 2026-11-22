package com.searchforge.indexer.index;

import com.searchforge.indexer.model.PostingsList;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable point-in-time snapshot of collection statistics, term postings,
 * and document lengths for a specific set of query terms.
 *
 * Guarantees read-isolation for search queries: readers observe a completely
 * consistent view of the index without multi-read skew, even while concurrent
 * ingestion threads mutate, replace, or delete documents.
 */
public record IndexQuerySnapshot(
        long totalDocuments,
        double averageDocumentLength,
        Map<String, PostingsList> termPostings,
        Map<Long, Integer> docLengths
) {
    public IndexQuerySnapshot {
        termPostings = termPostings != null ? Map.copyOf(termPostings) : Collections.emptyMap();
        docLengths = docLengths != null ? Map.copyOf(docLengths) : Collections.emptyMap();
    }

    public Optional<PostingsList> getPostings(String term) {
        if (term == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(termPostings.get(term));
    }

    public int getDocLength(long documentId) {
        return docLengths.getOrDefault(documentId, 0);
    }
}
