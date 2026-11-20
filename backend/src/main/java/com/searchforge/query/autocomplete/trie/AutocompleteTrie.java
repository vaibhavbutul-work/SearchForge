package com.searchforge.query.autocomplete.trie;

import com.searchforge.query.autocomplete.model.AutocompleteSuggestion;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom in-memory Weighted Prefix Trie for high-throughput autocomplete.
 *
 * Implements character-level prefix navigation, deterministic suggestion scoring,
 * and bounded Top-K candidate selection.
 */
public class AutocompleteTrie {

    private final TrieNode root;
    private final AtomicInteger termCount;

    public AutocompleteTrie() {
        this.root = new TrieNode();
        this.termCount = new AtomicInteger(0);
    }

    /**
     * Inserts an indexed term into the Trie with its collection and document frequencies.
     *
     * @param term Normalized vocabulary term.
     * @param ctf  Collection Term Frequency (total term occurrences).
     * @param df   Document Frequency (documents containing this term).
     */
    public void insert(String term, long ctf, int df) {
        if (term == null || term.isBlank()) {
            return;
        }

        String normalized = term.trim().toLowerCase(Locale.ROOT);
        double score = calculateScore(ctf, df);

        TrieNode current = root;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            current = current.getOrCreateChild(ch);
        }

        if (!current.isEndOfWord()) {
            termCount.incrementAndGet();
        }

        current.setEndOfWord(true);
        current.setTerm(normalized);
        current.setCtf(ctf);
        current.setDf(df);
        current.setScore(score);
    }

    /**
     * Retrieves the top-K autocomplete suggestions matching the given prefix.
     *
     * @param prefix Query prefix string.
     * @param limit  Maximum number of suggestions to return.
     * @return List of ranked suggestions sorted by score descending (with alphabetical tie-breaking).
     */
    public List<AutocompleteSuggestion> searchPrefix(String prefix, int limit) {
        if (prefix == null || prefix.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }

        String normalizedPrefix = prefix.trim().toLowerCase(Locale.ROOT);
        TrieNode prefixNode = findPrefixNode(normalizedPrefix);

        if (prefixNode == null) {
            return Collections.emptyList();
        }

        // Bounded Min-Heap: stores top-K candidates.
        // The head of the heap is the smallest candidate (lowest score).
        // When size > limit, poll() evicts the weakest candidate.
        PriorityQueue<AutocompleteSuggestion> minHeap = new PriorityQueue<>(
                limit + 1,
                (a, b) -> a.compareTo(b) * -1 // Invert to make min-heap based on natural order
        );

        collectDescendants(prefixNode, minHeap, limit);

        // Extract from min-heap and sort into final descending order
        List<AutocompleteSuggestion> results = new ArrayList<>(minHeap);
        Collections.sort(results); // Sorts according to natural order (score DESC, term ASC)

        return Collections.unmodifiableList(results);
    }

    /**
     * Checks if an exact word exists in the Trie.
     */
    public boolean contains(String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        TrieNode node = findPrefixNode(term.trim().toLowerCase(Locale.ROOT));
        return node != null && node.isEndOfWord();
    }

    /**
     * Returns the total number of distinct words indexed in the Trie.
     */
    public int size() {
        return termCount.get();
    }

    /**
     * Traverses character edges down the Trie to locate the prefix root node.
     */
    private TrieNode findPrefixNode(String prefix) {
        TrieNode current = root;
        for (int i = 0; i < prefix.length(); i++) {
            char ch = prefix.charAt(i);
            current = current.getChild(ch);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * Recursively traverses all subtree branches to gather terminal words into the bounded heap.
     */
    private void collectDescendants(TrieNode node, PriorityQueue<AutocompleteSuggestion> minHeap, int limit) {
        if (node == null) {
            return;
        }

        if (node.isEndOfWord()) {
            AutocompleteSuggestion suggestion = new AutocompleteSuggestion(
                    node.getTerm(),
                    node.getCtf(),
                    node.getDf(),
                    node.getScore()
            );

            minHeap.offer(suggestion);
            if (minHeap.size() > limit) {
                minHeap.poll(); // Evict weakest candidate
            }
        }

        for (TrieNode child : node.getChildren().values()) {
            collectDescendants(child, minHeap, limit);
        }
    }

    /**
     * Deterministic scoring formula:
     * Score(t) = ln(1 + ctf) * sqrt(df)
     *
     * Rewards widespread terms across documents while dampening high collection frequencies.
     * Rounds to 4 decimal places for clean, interpretable output.
     */
    public static double calculateScore(long ctf, int df) {
        double safeCtf = Math.max(0, ctf);
        double safeDf = Math.max(0, df);
        double raw = Math.log(1.0 + safeCtf) * Math.sqrt(safeDf);
        return Math.round(raw * 10000.0) / 10000.0;
    }
}
