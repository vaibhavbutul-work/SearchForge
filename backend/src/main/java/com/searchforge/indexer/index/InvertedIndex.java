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
}
