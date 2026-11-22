package com.searchforge.indexer.index;

import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.model.TermMetadata;
import com.searchforge.indexer.token.ProcessedToken;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core interface for the SearchForge Inverted Index.
 */
public interface InvertedIndex {

    /**
     * Inverts and indexes the token stream of a document.
     *
     * @param documentId Unique identifier of the document.
     * @param tokens     Processed tokens containing terms and positions.
     */
    void addDocument(long documentId, List<ProcessedToken> tokens);

    /**
     * Retrieves the postings list for a term.
     *
     * @param term Normalized/stemmed query term.
     * @return Optional containing the PostingsList if the term exists in the dictionary.
     */
    Optional<PostingsList> getPostings(String term);

    /**
     * Returns the total number of tokens (length) in a specific document.
     */
    int getDocLength(long documentId);

    /**
     * Returns the total count of distinct documents currently indexed (N).
     */
    int getTotalDocuments();

    /**
     * Returns a sorted list of all indexed document IDs across the corpus.
     */
    List<Long> getAllDocumentIds();

    /**
     * Returns the average document length across the entire indexed corpus (avgdl).
     */
    double getAverageDocumentLength();

    /**
     * Returns the number of unique terms in the dictionary (vocabulary size).
     */
    int getVocabularySize();

    /**
     * Returns dictionary metadata for all terms in the index.
     */
    Map<String, TermMetadata> getDictionaryMetadata();

    /**
     * Purges all terms, postings, and corpus metrics.
     */
    void clear();

    /**
     * Removes a document's postings and metadata from the index.
     *
     * @param documentId Document ID to remove.
     */
    void removeDocument(long documentId);

    /**
     * Directly inserts pre-aggregated term positions for a document without re-tokenizing.
     * Useful for cold startup warmup from persistent storage.
     *
     * @param documentId    Document ID.
     * @param docLength     Total token count in the document.
     * @param termPositions Map of term string to list of positions.
     */
    void addDocumentPostings(long documentId, int docLength, Map<String, List<Integer>> termPositions);

    /**
     * Atomically replaces the postings and document length of an existing or new document.
     * Guarantees that concurrent readers never observe intermediate partial state or zero length.
     *
     * @param documentId Unique identifier of the document.
     * @param tokens     New processed tokens.
     */
    void replaceDocument(long documentId, List<ProcessedToken> tokens);

    /**
     * Captures a point-in-time, read-isolated snapshot of collection statistics,
     * term postings, and candidate document lengths for the specified query terms.
     *
     * @param queryTerms Terms to include in the snapshot.
     * @return Immutable {@link IndexQuerySnapshot}.
     */
    IndexQuerySnapshot getSnapshot(java.util.Collection<String> queryTerms);
}
