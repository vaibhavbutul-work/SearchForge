package com.searchforge.ranking.retrieval;

import com.searchforge.indexer.retrieval.ast.*;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;

import java.util.*;

/**
 * Utility extracting positive scoring terms and exact quoted phrases from a Boolean Query AST.
 *
 * Traversal Rules:
 * 1. TermNode: Emits normalized/stemmed term as a positive scoring term.
 * 2. PhraseNode: Emits constituent terms as positive scoring terms AND records the phrase for phrase boosting.
 * 3. AndNode / OrNode: Recurses into both subtrees.
 * 4. NotNode: Recurses ONLY into the positive operand (if present). Negated terms are strictly exclusion filters
 *    and NEVER contribute positive relevance score.
 */
public final class PositiveTermExtractor {

    private PositiveTermExtractor() {
        // Utility class
    }

    /**
     * Container holding extracted positive scoring terms and phrases.
     */
    public record ScoringElements(
            Set<String> positiveTerms,
            List<String> phrases
    ) {
        public ScoringElements {
            positiveTerms = positiveTerms != null ? Set.copyOf(positiveTerms) : Set.of();
            phrases = phrases != null ? List.copyOf(phrases) : List.of();
        }
    }

    /**
     * Extracts positive scoring terms and quoted phrases from a Boolean query AST.
     *
     * @param root      Root of the QueryNode AST.
     * @param tokenizer Tokenizer used for consistent linguistic normalization and stemming.
     * @return Extracted {@link ScoringElements}.
     */
    public static ScoringElements extract(QueryNode root, Tokenizer tokenizer) {
        if (root == null || tokenizer == null) {
            return new ScoringElements(Set.of(), List.of());
        }

        Set<String> terms = new LinkedHashSet<>();
        List<String> phrases = new ArrayList<>();
        traverse(root, terms, phrases, tokenizer);

        return new ScoringElements(terms, phrases);
    }

    private static void traverse(QueryNode node, Set<String> terms, List<String> phrases, Tokenizer tokenizer) {
        if (node == null) {
            return;
        }

        switch (node) {
            case TermNode t -> {
                List<ProcessedToken> tokens = tokenizer.tokenize(t.term());
                if (!tokens.isEmpty()) {
                    terms.add(tokens.get(0).term());
                } else {
                    terms.add(t.term().toLowerCase(Locale.ROOT));
                }
            }
            case PhraseNode p -> {
                phrases.add(p.phrase());
                List<ProcessedToken> tokens = tokenizer.tokenize(p.phrase());
                for (ProcessedToken pt : tokens) {
                    terms.add(pt.term());
                }
            }
            case AndNode a -> {
                traverse(a.left(), terms, phrases, tokenizer);
                traverse(a.right(), terms, phrases, tokenizer);
            }
            case OrNode o -> {
                traverse(o.left(), terms, phrases, tokenizer);
                traverse(o.right(), terms, phrases, tokenizer);
            }
            case NotNode n -> {
                // Critical IR Invariant: Only the positive branch contributes scoring terms!
                // Negated terms filter documents out but never add positive BM25 relevance score.
                if (n.positive() != null) {
                    traverse(n.positive(), terms, phrases, tokenizer);
                }
            }
        }
    }
}
