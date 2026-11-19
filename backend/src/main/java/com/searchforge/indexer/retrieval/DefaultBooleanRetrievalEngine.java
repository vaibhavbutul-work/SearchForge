package com.searchforge.indexer.retrieval;

import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.retrieval.ast.*;
import com.searchforge.indexer.search.PositionalIndexSearcher;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Production implementation of the SearchForge Boolean Retrieval Engine.
 *
 * Traverses the QueryNode AST and executes two-pointer linear merge operations
 * (AND, OR, NOT) against the in-memory inverted index and positional searcher.
 */
@Service
public class DefaultBooleanRetrievalEngine implements BooleanRetrievalEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultBooleanRetrievalEngine.class);

    private final InvertedIndex invertedIndex;
    private final PositionalIndexSearcher positionalSearcher;
    private final Tokenizer tokenizer;
    private final BooleanQueryParser queryParser;

    @Autowired
    public DefaultBooleanRetrievalEngine(InvertedIndex invertedIndex,
                                         PositionalIndexSearcher positionalSearcher,
                                         Tokenizer tokenizer) {
        this.invertedIndex = invertedIndex;
        this.positionalSearcher = positionalSearcher;
        this.tokenizer = tokenizer;
        this.queryParser = new BooleanQueryParser();
    }

    public DefaultBooleanRetrievalEngine(InvertedIndex invertedIndex,
                                         PositionalIndexSearcher positionalSearcher,
                                         Tokenizer tokenizer,
                                         BooleanQueryParser queryParser) {
        this.invertedIndex = invertedIndex;
        this.positionalSearcher = positionalSearcher;
        this.tokenizer = tokenizer;
        this.queryParser = queryParser != null ? queryParser : new BooleanQueryParser();
    }

    @Override
    public BooleanSearchResult search(String queryString) {
        long startTime = System.currentTimeMillis();

        if (queryString == null || queryString.isBlank()) {
            return BooleanSearchResult.empty(0);
        }

        Optional<QueryNode> astRoot;
        try {
            astRoot = queryParser.parse(queryString);
        } catch (Exception e) {
            log.warn("Failed to parse Boolean query '{}': {}", queryString, e.getMessage());
            long duration = System.currentTimeMillis() - startTime;
            return BooleanSearchResult.empty(duration);
        }

        if (astRoot.isEmpty()) {
            long duration = System.currentTimeMillis() - startTime;
            return BooleanSearchResult.empty(duration);
        }

        return search(astRoot.get());
    }

    @Override
    public BooleanSearchResult search(QueryNode astRoot) {
        long startTime = System.currentTimeMillis();

        if (astRoot == null) {
            return BooleanSearchResult.empty(0);
        }

        List<Long> matchedDocIds = evaluate(astRoot);
        long duration = System.currentTimeMillis() - startTime;

        return new BooleanSearchResult(matchedDocIds, matchedDocIds.size(), duration);
    }

    private List<Long> evaluate(QueryNode node) {
        if (node instanceof TermNode termNode) {
            return evaluateTerm(termNode.term());
        } else if (node instanceof PhraseNode phraseNode) {
            return evaluatePhrase(phraseNode.phrase());
        } else if (node instanceof AndNode andNode) {
            List<Long> left = evaluate(andNode.left());
            if (left.isEmpty()) {
                return List.of();
            }
            List<Long> right = evaluate(andNode.right());
            return PostingsMerger.intersect(left, right);
        } else if (node instanceof OrNode orNode) {
            List<Long> left = evaluate(orNode.left());
            List<Long> right = evaluate(orNode.right());
            return PostingsMerger.union(left, right);
        } else if (node instanceof NotNode notNode) {
            List<Long> negative = evaluate(notNode.negative());
            if (notNode.positive() != null) {
                List<Long> positive = evaluate(notNode.positive());
                return PostingsMerger.difference(positive, negative);
            } else {
                List<Long> allDocs = invertedIndex.getAllDocumentIds();
                return PostingsMerger.difference(allDocs, negative);
            }
        }
        return List.of();
    }

    private List<Long> evaluateTerm(String rawTerm) {
        List<ProcessedToken> tokens = tokenizer.tokenize(rawTerm);
        if (tokens.isEmpty()) {
            return List.of();
        }
        String term = tokens.get(0).term();
        Optional<PostingsList> postingsOpt = invertedIndex.getPostings(term);
        if (postingsOpt.isEmpty()) {
            return List.of();
        }
        return postingsOpt.get().getPostings().stream()
                .map(Posting::documentId)
                .toList();
    }

    private List<Long> evaluatePhrase(String phrase) {
        return positionalSearcher.searchPhraseDocIds(phrase);
    }
}
