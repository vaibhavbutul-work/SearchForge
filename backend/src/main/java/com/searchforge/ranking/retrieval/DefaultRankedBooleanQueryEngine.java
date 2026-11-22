package com.searchforge.ranking.retrieval;

import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.retrieval.BooleanQueryParser;
import com.searchforge.indexer.retrieval.BooleanRetrievalEngine;
import com.searchforge.indexer.retrieval.BooleanSearchResult;
import com.searchforge.indexer.retrieval.ast.QueryNode;
import com.searchforge.indexer.search.PhraseMatch;
import com.searchforge.indexer.search.PositionalIndexSearcher;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.ranking.model.CollectionStats;
import com.searchforge.ranking.model.DocumentStats;
import com.searchforge.ranking.model.ScoredDocument;
import com.searchforge.ranking.scorer.Bm25Scorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Production implementation of {@link RankedBooleanQueryEngine}.
 *
 * Combines Boolean logic filtering with relevance ranking:
 * 1. Executes Boolean candidate retrieval via {@link BooleanRetrievalEngine}.
 * 2. Extracts positive scoring terms and quoted phrases using {@link PositiveTermExtractor}.
 * 3. Scores candidate documents using in-memory BM25 (zero database I/O).
 * 4. Applies positional phrase boosts for exact phrase matches via {@link PositionalIndexSearcher}.
 * 5. Returns top-K {@link RankedSearchResult}s sorted descending by relevance.
 */
@Service
public class DefaultRankedBooleanQueryEngine implements RankedBooleanQueryEngine {

    private final BooleanQueryParser booleanQueryParser;
    private final BooleanRetrievalEngine booleanRetrievalEngine;
    private final InvertedIndex invertedIndex;
    private final Bm25Scorer bm25Scorer;
    private final Tokenizer tokenizer;
    private final PositionalIndexSearcher positionalIndexSearcher;
    private final ActiveDocumentFilter activeDocumentFilter;

    public DefaultRankedBooleanQueryEngine(BooleanRetrievalEngine booleanRetrievalEngine,
                                           InvertedIndex invertedIndex,
                                           Bm25Scorer bm25Scorer,
                                           Tokenizer tokenizer,
                                           PositionalIndexSearcher positionalIndexSearcher) {
        this(new BooleanQueryParser(), booleanRetrievalEngine, invertedIndex, bm25Scorer, tokenizer, positionalIndexSearcher, null);
    }

    @Autowired
    public DefaultRankedBooleanQueryEngine(BooleanRetrievalEngine booleanRetrievalEngine,
                                           InvertedIndex invertedIndex,
                                           Bm25Scorer bm25Scorer,
                                           Tokenizer tokenizer,
                                           PositionalIndexSearcher positionalIndexSearcher,
                                           @Autowired(required = false) ActiveDocumentFilter activeDocumentFilter) {
        this(new BooleanQueryParser(), booleanRetrievalEngine, invertedIndex, bm25Scorer, tokenizer, positionalIndexSearcher, activeDocumentFilter);
    }

    public DefaultRankedBooleanQueryEngine(BooleanQueryParser booleanQueryParser,
                                           BooleanRetrievalEngine booleanRetrievalEngine,
                                           InvertedIndex invertedIndex,
                                           Bm25Scorer bm25Scorer,
                                           Tokenizer tokenizer,
                                           PositionalIndexSearcher positionalIndexSearcher) {
        this(booleanQueryParser, booleanRetrievalEngine, invertedIndex, bm25Scorer, tokenizer, positionalIndexSearcher, null);
    }

    public DefaultRankedBooleanQueryEngine(BooleanQueryParser booleanQueryParser,
                                           BooleanRetrievalEngine booleanRetrievalEngine,
                                           InvertedIndex invertedIndex,
                                           Bm25Scorer bm25Scorer,
                                           Tokenizer tokenizer,
                                           PositionalIndexSearcher positionalIndexSearcher,
                                           ActiveDocumentFilter activeDocumentFilter) {
        this.booleanQueryParser = Objects.requireNonNull(booleanQueryParser, "BooleanQueryParser cannot be null");
        this.booleanRetrievalEngine = Objects.requireNonNull(booleanRetrievalEngine, "BooleanRetrievalEngine cannot be null");
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "InvertedIndex cannot be null");
        this.bm25Scorer = Objects.requireNonNull(bm25Scorer, "Bm25Scorer cannot be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
        this.positionalIndexSearcher = Objects.requireNonNull(positionalIndexSearcher, "PositionalIndexSearcher cannot be null");
        this.activeDocumentFilter = activeDocumentFilter;
    }

    @Override
    public List<RankedSearchResult> searchRankedBoolean(String booleanQuery, int limit) {
        if (booleanQuery == null || booleanQuery.isBlank() || limit <= 0) {
            return List.of();
        }

        // 1. Parse Boolean Query into AST
        Optional<QueryNode> astOpt = booleanQueryParser.parse(booleanQuery);
        if (astOpt.isEmpty()) {
            return List.of();
        }
        QueryNode ast = astOpt.get();

        // 2. Execute Boolean Candidate Retrieval
        BooleanSearchResult boolResult = booleanRetrievalEngine.search(ast);
        List<Long> candidateDocIds = boolResult.documentIds();
        if (activeDocumentFilter != null) {
            candidateDocIds = candidateDocIds.stream()
                    .filter(activeDocumentFilter::isActive)
                    .toList();
        }
        if (candidateDocIds.isEmpty()) {
            return List.of();
        }

        // 3. Extract Positive Scoring Terms and Quoted Phrases
        PositiveTermExtractor.ScoringElements scoringElements = PositiveTermExtractor.extract(ast, tokenizer);
        Set<String> positiveTerms = scoringElements.positiveTerms();
        List<String> phrases = scoringElements.phrases();

        long totalDocuments = invertedIndex.getTotalDocuments();
        if (totalDocuments <= 0) {
            return List.of();
        }
        double avgDocLength = invertedIndex.getAverageDocumentLength();

        // If no positive terms exist (e.g. pure negation "NOT legacy"), return candidates with baseline score
        if (positiveTerms.isEmpty()) {
            return packageBaselineResults(candidateDocIds, limit);
        }

        // 4. Extract Collection Statistics for Positive Terms
        Map<String, Long> dfMap = new HashMap<>();
        for (String term : positiveTerms) {
            long df = invertedIndex.getPostings(term)
                    .map(PostingsList::size)
                    .orElse(0);
            dfMap.put(term, df);
        }
        CollectionStats collectionStats = new CollectionStats(totalDocuments, avgDocLength, dfMap);

        // 5. Build DocumentStats for Candidate Documents
        List<DocumentStats> docStatsList = new ArrayList<>(candidateDocIds.size());
        for (Long docId : candidateDocIds) {
            int docLength = invertedIndex.getDocLength(docId);
            Map<String, Integer> termFrequencies = new HashMap<>();
            for (String term : positiveTerms) {
                Optional<PostingsList> postingsOpt = invertedIndex.getPostings(term);
                if (postingsOpt.isPresent()) {
                    Optional<Posting> postingOpt = postingsOpt.get().getPostingForDocument(docId);
                    postingOpt.ifPresent(p -> termFrequencies.put(term, p.termFrequency()));
                }
            }
            docStatsList.add(new DocumentStats(docId, docLength, termFrequencies));
        }

        // 6. Score Candidates using BM25
        List<ScoredDocument> scoredDocs = bm25Scorer.scoreDocuments(
                docStatsList,
                positiveTerms.stream().toList(),
                collectionStats
        );

        // 7. Apply Phrase Boost Enhancements
        Map<Long, Double> phraseBonuses = new HashMap<>();
        Map<Long, Map<String, Double>> phraseScoreBreakdown = new HashMap<>();

        if (!phrases.isEmpty()) {
            Set<Long> candidateSet = new HashSet<>(candidateDocIds);
            for (String phrase : phrases) {
                List<PhraseMatch> matches = positionalIndexSearcher.searchPhrase(phrase);
                for (PhraseMatch match : matches) {
                    if (candidateSet.contains(match.documentId())) {
                        double bonus = DEFAULT_PHRASE_BOOST * match.matchCount();
                        phraseBonuses.merge(match.documentId(), bonus, Double::sum);
                        phraseScoreBreakdown
                                .computeIfAbsent(match.documentId(), k -> new HashMap<>())
                                .put("PHRASE: \"" + phrase + "\"", bonus);
                    }
                }
            }
        }

        // 8. Integrate Phrase Bonuses and Sort
        List<ScoredDocument> finalScoredDocs = new ArrayList<>(scoredDocs.size());
        for (ScoredDocument doc : scoredDocs) {
            double extraBonus = phraseBonuses.getOrDefault(doc.docId(), 0.0);
            double newScore = doc.score() + extraBonus;

            Map<String, Double> mergedTermScores = new HashMap<>(doc.termScores());
            Map<String, Double> phraseScores = phraseScoreBreakdown.get(doc.docId());
            if (phraseScores != null) {
                mergedTermScores.putAll(phraseScores);
            }

            finalScoredDocs.add(new ScoredDocument(doc.docId(), newScore, mergedTermScores));
        }

        // Sort descending by score, ascending by docId
        Collections.sort(finalScoredDocs);

        // 9. Package Top-K Results
        List<RankedSearchResult> results = new ArrayList<>();
        int rank = 1;
        for (ScoredDocument doc : finalScoredDocs) {
            if (doc.score() <= 0.0) {
                continue;
            }
            if (results.size() >= limit) {
                break;
            }

            List<String> matchedTerms = doc.termScores().keySet().stream()
                    .sorted()
                    .toList();

            results.add(new RankedSearchResult(
                    doc.docId(),
                    doc.score(),
                    rank++,
                    matchedTerms,
                    doc.termScores()
            ));
        }

        return Collections.unmodifiableList(results);
    }

    @Override
    public List<RankedSearchResult> searchFiltered(String freeTextQuery, String booleanFilter, int limit) {
        if (freeTextQuery == null || freeTextQuery.isBlank() || limit <= 0) {
            return List.of();
        }

        // If no filter is provided, fall back to pure ranked search logic
        if (booleanFilter == null || booleanFilter.isBlank()) {
            return searchRankedBoolean(freeTextQuery, limit);
        }

        // 1. Evaluate Boolean Filter to get allowed candidate document universe
        Optional<QueryNode> filterAstOpt = booleanQueryParser.parse(booleanFilter);
        if (filterAstOpt.isEmpty()) {
            return searchRankedBoolean(freeTextQuery, limit);
        }
        QueryNode filterAst = filterAstOpt.get();
        BooleanSearchResult filterResult = booleanRetrievalEngine.search(filterAst);
        Set<Long> allowedDocIds = new HashSet<>(filterResult.documentIds());

        if (allowedDocIds.isEmpty()) {
            return List.of();
        }

        // 2. Tokenize free-text query
        List<ProcessedToken> tokens = tokenizer.tokenize(freeTextQuery);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> queryTerms = tokens.stream().map(ProcessedToken::term).toList();
        Set<String> uniqueTerms = new LinkedHashSet<>(queryTerms);

        long totalDocuments = invertedIndex.getTotalDocuments();
        if (totalDocuments <= 0) {
            return List.of();
        }
        double avgDocLength = invertedIndex.getAverageDocumentLength();

        Map<String, Long> dfMap = new HashMap<>();
        for (String term : uniqueTerms) {
            long df = invertedIndex.getPostings(term).map(PostingsList::size).orElse(0);
            dfMap.put(term, df);
        }
        CollectionStats collectionStats = new CollectionStats(totalDocuments, avgDocLength, dfMap);

        // 3. Collect candidate stats strictly restricted to allowedDocIds
        Map<Long, Map<String, Integer>> candidateTermFrequencies = new HashMap<>();
        for (String term : uniqueTerms) {
            Optional<PostingsList> postingsOpt = invertedIndex.getPostings(term);
            if (postingsOpt.isEmpty()) {
                continue;
            }
            for (Posting posting : postingsOpt.get().getPostings()) {
                if (allowedDocIds.contains(posting.documentId())) {
                    candidateTermFrequencies
                            .computeIfAbsent(posting.documentId(), k -> new HashMap<>())
                            .put(term, posting.termFrequency());
                }
            }
        }

        if (candidateTermFrequencies.isEmpty()) {
            return List.of();
        }

        List<DocumentStats> candidateStats = new ArrayList<>(candidateTermFrequencies.size());
        for (Map.Entry<Long, Map<String, Integer>> entry : candidateTermFrequencies.entrySet()) {
            long docId = entry.getKey();
            int docLength = invertedIndex.getDocLength(docId);
            candidateStats.add(new DocumentStats(docId, docLength, entry.getValue()));
        }

        // 4. Score and Rank
        List<ScoredDocument> scoredDocs = bm25Scorer.scoreDocuments(candidateStats, queryTerms, collectionStats);

        List<RankedSearchResult> results = new ArrayList<>();
        int rank = 1;
        for (ScoredDocument doc : scoredDocs) {
            if (doc.score() <= 0.0) {
                continue;
            }
            if (results.size() >= limit) {
                break;
            }
            List<String> matchedTerms = doc.termScores().keySet().stream().sorted().toList();
            results.add(new RankedSearchResult(
                    doc.docId(),
                    doc.score(),
                    rank++,
                    matchedTerms,
                    doc.termScores()
            ));
        }

        return Collections.unmodifiableList(results);
    }

    private List<RankedSearchResult> packageBaselineResults(List<Long> docIds, int limit) {
        List<RankedSearchResult> results = new ArrayList<>();
        int rank = 1;
        for (Long docId : docIds) {
            if (results.size() >= limit) {
                break;
            }
            results.add(new RankedSearchResult(
                    docId,
                    1.0,
                    rank++,
                    List.of("BOOLEAN_MATCH"),
                    Map.of("MATCH", 1.0)
            ));
        }
        return Collections.unmodifiableList(results);
    }
}
