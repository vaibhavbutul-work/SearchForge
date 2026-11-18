package com.searchforge.indexer.token;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter providing O(1) checks for common English stop words.
 *
 * Stop words are high-frequency, low-entropy grammatical terms (e.g., "the", "and", "is")
 * that carry negligible semantic information and bloat inverted indexes without search value.
 */
@Component
public class StopWordFilter {

    private static final Set<String> DEFAULT_STOP_WORDS;

    static {
        Set<String> words = new HashSet<>();
        Collections.addAll(words,
                "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
                "any", "are", "aren't", "arent", "as", "at", "be", "because", "been", "before",
                "being", "below", "between", "both", "but", "by", "can", "cannot", "can't",
                "cant", "could", "couldn't", "couldnt", "did", "didn't", "didnt", "do", "does",
                "doesn't", "doesnt", "doing", "don't", "dont", "down", "during", "each", "few",
                "for", "from", "further", "had", "hadn't", "hadnt", "has", "hasn't", "hasnt",
                "have", "haven't", "havent", "having", "he", "her", "here", "hers", "herself",
                "him", "himself", "his", "how", "i", "if", "in", "into", "is", "isn't", "isnt",
                "it", "it's", "its", "itself", "just", "me", "more", "most", "must", "my",
                "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other",
                "ought", "our", "ours", "ourselves", "out", "over", "own", "same", "she",
                "should", "shouldn't", "shouldnt", "so", "some", "such", "than", "that", "that's",
                "the", "their", "theirs", "them", "themselves", "then", "there", "these", "they",
                "this", "those", "through", "to", "too", "under", "until", "up", "very", "was",
                "wasn't", "wasnt", "we", "were", "weren't", "werent", "what", "when", "where",
                "which", "while", "who", "whom", "why", "will", "with", "won't", "wont", "would",
                "wouldn't", "wouldnt", "you", "your", "yours", "yourself", "yourselves"
        );
        DEFAULT_STOP_WORDS = Collections.unmodifiableSet(words);
    }

    private final Set<String> stopWords;

    public StopWordFilter() {
        this(DEFAULT_STOP_WORDS);
    }

    public StopWordFilter(Set<String> customStopWords) {
        this.stopWords = (customStopWords != null)
                ? Collections.unmodifiableSet(new HashSet<>(customStopWords))
                : DEFAULT_STOP_WORDS;
    }

    /**
     * Checks if a normalized word is a stop word.
     *
     * @param term Normalized, lowercased word term.
     * @return true if the term is a stop word, false otherwise.
     */
    public boolean isStopWord(String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        return stopWords.contains(term);
    }

    /**
     * Returns an unmodifiable view of the active stop words set.
     */
    public Set<String> getStopWords() {
        return stopWords;
    }

    /**
     * Returns total count of stop words in active dictionary.
     */
    public int size() {
        return stopWords.size();
    }
}
