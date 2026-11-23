package com.searchforge.indexer.model;

import java.util.ArrayList;
import java.util.Arrays;
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
            int[] merged = mergeSortedPositions(existing.rawPositions(), posting.rawPositions());
            postings.set(index, new Posting(posting.documentId(), merged.length, merged));
            totalTermFrequency += (merged.length - existing.termFrequency());
        } else {
            int insertIndex = -(index + 1);
            postings.add(insertIndex, posting);
            totalTermFrequency += posting.termFrequency();
        }
    }

    /**
     * Atomically inserts or completely replaces a Posting in the list, preserving ascending documentId order.
     * Unlike {@link #addPosting(Posting)}, this does not merge positions, ensuring clean atomic replacement.
     *
     * @param posting The posting to insert or replace.
     */
    public synchronized void putPosting(Posting posting) {
        if (posting == null) {
            return;
        }

        int index = Collections.binarySearch(postings, posting);
        if (index >= 0) {
            Posting existing = postings.get(index);
            postings.set(index, posting);
            totalTermFrequency += (posting.termFrequency() - existing.termFrequency());
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
     * Note: Creates a defensive copy. For zero-allocation read-only iteration, use {@link #getPostingsView()}
     * or {@link #forEachPosting(java.util.function.Consumer)}.
     */
    public synchronized List<Posting> getPostings() {
        return List.copyOf(postings);
    }

    /**
     * Returns an unmodifiable view over the postings list without copying the underlying array.
     */
    public synchronized List<Posting> getPostingsView() {
        return Collections.unmodifiableList(postings);
    }

    /**
     * Performs a zero-allocation sequential iteration over all postings.
     */
    public synchronized void forEachPosting(java.util.function.Consumer<Posting> consumer) {
        for (Posting p : postings) {
            consumer.accept(p);
        }
    }

    /**
     * Direct indexed access to a posting by ordinal offset.
     */
    public synchronized Posting getPosting(int index) {
        return postings.get(index);
    }

    /**
     * Returns a compact primitive array of all document IDs in this postings list.
     */
    public synchronized long[] getDocumentIds() {
        long[] ids = new long[postings.size()];
        for (int i = 0; i < postings.size(); i++) {
            ids[i] = postings.get(i).documentId();
        }
        return ids;
    }

    /**
     * Returns a compact primitive array of term frequencies aligned with getDocumentIds().
     */
    public synchronized int[] getTermFrequencies() {
        int[] tfs = new int[postings.size()];
        for (int i = 0; i < postings.size(); i++) {
            tfs[i] = postings.get(i).termFrequency();
        }
        return tfs;
    }

    /**
     * Finds the posting for a specific documentId via zero-allocation binary search.
     */
    public synchronized Optional<Posting> getPostingForDocument(long documentId) {
        int index = binarySearchByDocId(documentId);
        if (index >= 0) {
            return Optional.of(postings.get(index));
        }
        return Optional.empty();
    }

    /**
     * Checks if a documentId is present in this postings list via zero-allocation binary search.
     */
    public synchronized boolean containsDocument(long documentId) {
        return binarySearchByDocId(documentId) >= 0;
    }

    /**
     * Removes a posting for the specified documentId if present.
     *
     * @param documentId Document ID to remove.
     * @return true if a posting was removed, false otherwise.
     */
    public synchronized boolean removePosting(long documentId) {
        int index = binarySearchByDocId(documentId);
        if (index >= 0) {
            Posting removed = postings.remove(index);
            totalTermFrequency -= removed.termFrequency();
            return true;
        }
        return false;
    }

    /**
     * Internal zero-allocation binary search by primitive documentId.
     */
    private int binarySearchByDocId(long targetDocId) {
        int low = 0;
        int high = postings.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midDocId = postings.get(mid).documentId();

            if (midDocId < targetDocId) {
                low = mid + 1;
            } else if (midDocId > targetDocId) {
                high = mid - 1;
            } else {
                return mid; // found
            }
        }
        return -(low + 1); // not found
    }

    @Override
    public synchronized Iterator<Posting> iterator() {
        return List.copyOf(postings).iterator();
    }

    /**
     * Creates an independent, point-in-time snapshot copy of this PostingsList.
     * Subsequent mutations to the original PostingsList will not affect the snapshot.
     */
    public synchronized PostingsList snapshot() {
        PostingsList copy = new PostingsList();
        copy.postings.addAll(this.postings);
        copy.totalTermFrequency = this.totalTermFrequency;
        return copy;
    }

    /**
     * Efficiently merges two ascendingly sorted primitive position arrays in O(N1 + N2) time
     * without boxing or auxiliary collection instantiation, eliminating duplicate positions.
     */
    private static int[] mergeSortedPositions(int[] a, int[] b) {
        if (a == null || a.length == 0) return (b != null) ? b.clone() : new int[0];
        if (b == null || b.length == 0) return a.clone();
        int[] temp = new int[a.length + b.length];
        int i = 0, j = 0, k = 0;
        while (i < a.length && j < b.length) {
            if (a[i] < b[j]) {
                temp[k++] = a[i++];
            } else if (a[i] > b[j]) {
                temp[k++] = b[j++];
            } else {
                temp[k++] = a[i++];
                j++; // duplicate eliminated
            }
        }
        while (i < a.length) temp[k++] = a[i++];
        while (j < b.length) temp[k++] = b[j++];
        return (k == temp.length) ? temp : Arrays.copyOf(temp, k);
    }
}
