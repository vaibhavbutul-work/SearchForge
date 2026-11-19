package com.searchforge.indexer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Thread-safe ordered sequence of Posting objects for a single term,
 * maintained in strictly ascending order by documentId.
 */
public class PostingsList implements Iterable<Posting> {

    private final List<Posting> postings = new ArrayList<>();
    private long totalTermFrequency = 0;

    /**
     * Inserts or updates a Posting in the list, preserving ascending documentId order.
     *
     * @param posting The posting to add.
     */
    public synchronized void addPosting(Posting posting) {
        if (posting == null) {
            return;
        }

        int index = Collections.binarySearch(postings, posting);
        if (index >= 0) {
            // Document already present; merge positions and update term frequency
            Posting existing = postings.get(index);
            List<Integer> mergedPositions = new ArrayList<>(existing.positions());
            for (int pos : posting.positions()) {
                if (!mergedPositions.contains(pos)) {
                    mergedPositions.add(pos);
                }
            }
            Collections.sort(mergedPositions);
            postings.set(index, new Posting(posting.documentId(), mergedPositions.size(), mergedPositions));
            totalTermFrequency += (mergedPositions.size() - existing.termFrequency());
        } else {
            int insertIndex = -(index + 1);
            postings.add(insertIndex, posting);
            totalTermFrequency += posting.termFrequency();
        }
    }

    /**
     * Returns the document frequency (df), i.e., number of distinct documents containing the term.
     */
    public synchronized int size() {
        return postings.size();
    }

    /**
     * Returns the total occurrences of this term across all documents (corpus term frequency).
     */
    public synchronized long getTotalTermFrequency() {
        return totalTermFrequency;
    }

    /**
     * Returns an unmodifiable snapshot of the postings list sorted by documentId ascending.
     */
    public synchronized List<Posting> getPostings() {
        return List.copyOf(postings);
    }

    /**
     * Finds the posting for a specific documentId via binary search.
     */
    public synchronized Optional<Posting> getPostingForDocument(long documentId) {
        int index = Collections.binarySearch(postings, new Posting(documentId, 1, List.of()));
        if (index >= 0) {
            return Optional.of(postings.get(index));
        }
        return Optional.empty();
    }

    /**
     * Checks if a documentId is present in this postings list.
     */
    public synchronized boolean containsDocument(long documentId) {
        return Collections.binarySearch(postings, new Posting(documentId, 1, List.of())) >= 0;
    }

    /**
     * Removes a posting for the specified documentId if present.
     *
     * @param documentId Document ID to remove.
     * @return true if a posting was removed, false otherwise.
     */
    public synchronized boolean removePosting(long documentId) {
        int index = Collections.binarySearch(postings, new Posting(documentId, 1, List.of()));
        if (index >= 0) {
            Posting removed = postings.remove(index);
            totalTermFrequency -= removed.termFrequency();
            return true;
        }
        return false;
    }

    @Override
    public synchronized Iterator<Posting> iterator() {
        return List.copyOf(postings).iterator();
    }
}
