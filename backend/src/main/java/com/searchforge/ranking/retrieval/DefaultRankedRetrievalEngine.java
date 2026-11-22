package com.searchforge.ranking.retrieval;

import com.searchforge.indexer.index.IndexQuerySnapshot;
import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.lifecycle.filter.ActiveDocumentFilter;
import com.searchforge.query.synonym.model.ExpandedQuery;
import com.searchforge.query.synonym.model.ExpandedTerm;
import com.searchforge.query.synonym.service.SynonymExpansionService;
import com.searchforge.ranking.model.CollectionStats;
import com.searchforge.ranking.model.DocumentStats;
import com.searchforge.ranking.model.ScoredDocument;
import com.searchforge.ranking.scorer.Bm25Scorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Production implementation of {@link RankedRetrievalEngine} executing free-text BM25 search.
 *
 * Implements the standard information retrieval pipeline:
 * 1. Linguistic query processing (normalization, tokenization, stop-word removal, stemming).
 * 2. Optional synonym expansion with configurable discount weighting.
 * 3. In-memory collection statistics extraction from {@link InvertedIndex}.
 * 4. Fast Term-At-A-Time (TAAT) candidate generation via posting list union.
 * 5. Pure in-memory BM25 scoring with zero database calls.
 * 6. Top-K truncation and ranked result packaging.
 */
@Service
public class DefaultRankedRetrievalEngine implements RankedRetrievalEngine {

    private final InvertedIndex invertedIndex;
    private final Bm25Scorer bm25Scorer;
    private final Tokenizer tokenizer;
    private final SynonymExpansionService synonymExpansionService;
    private final ActiveDocumentFilter activeDocumentFilter;

    public DefaultRankedRetrievalEngine(InvertedIndex invertedIndex,
                                        Bm25Scorer bm25Scorer,
                                        Tokenizer tokenizer) {
        this(invertedIndex, bm25Scorer, tokenizer, null, null);
    }

    public DefaultRankedRetrievalEngine(InvertedIndex invertedIndex,
                                        Bm25Scorer bm25Scorer,
                                        Tokenizer tokenizer,
                                        SynonymExpansionService synonymExpansionService) {
        this(invertedIndex, bm25Scorer, tokenizer, synonymExpansionService, null);
    }

    @Autowired
    public DefaultRankedRetrievalEngine(InvertedIndex invertedIndex,
                                        Bm25Scorer bm25Scorer,
                                        Tokenizer tokenizer,
                                        @Autowired(required = false) SynonymExpansionService synonymExpansionService,
                                        @Autowired(required = false) ActiveDocumentFilter activeDocumentFilter) {
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "InvertedIndex cannot be null");
        this.bm25Scorer = Objects.requireNonNull(bm25Scorer, "Bm25Scorer cannot be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
        this.synonymExpansionService = synonymExpansionService;
        this.activeDocumentFilter = activeDocumentFilter;
    }

    @Override
    public List<RankedSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        // Apply synonym expansion if configured and query is not an exact quoted phrase
        if (synonymExpansionService != null && !query.contains("\"")) {
            ExpandedQuery expanded = synonymExpansionService.expandQuery(query);
            if (expanded != null && expanded.isExpanded()) {
                return search(expanded, limit);
            }
        }

        return searchStandard(query, limit);
    }

    @Override
    public List<RankedSearchResult> search(ExpandedQuery expandedQuery, int limit) {
        if (expandedQuery == null || limit <= 0) {
            return List.of();
        }
        if (!expandedQuery.isExpanded()) {
            return searchStandard(expandedQuery.originalQuery(), limit);
        }

        // 1. Linguistic processing of expanded terms to extract stems and maximum weights
        Map<String, Double> stemWeights = new HashMap<>();
        for (ExpandedTerm expTerm : expandedQuery.terms()) {
            List<ProcessedToken> tokens = tokenizer.tokenize(expTerm.term());
            for (ProcessedToken token : tokens) {
                String stem = token.term();
                stemWeights.merge(stem, expTerm.weight(), Math::max);
            }
        }

        if (stemWeights.isEmpty()) {
            return List.of();
        }

        // 2. Atomic Collection Statistics & Postings Snapshot Extraction
        IndexQuerySnapshot snapshot = invertedIndex.getSnapshot(stemWeights.keySet());
        long totalDocuments = snapshot.totalDocuments();
        if (totalDocuments <= 0) {
            return List.of();
        }
        double avgDocLength = snapshot.averageDocumentLength();

        Map<String, Long> dfMap = new HashMap<>();
        for (String term : stemWeights.keySet()) {
            long df = snapshot.getPostings(term)
                    .map(pl -> (long) pl.size())
                    .orElse(0L);
            dfMap.put(term, df);
        }
        CollectionStats collectionStats = new CollectionStats(totalDocuments, avgDocLength, dfMap);

        // 3. Candidate Generation via Term-At-A-Time (TAAT) Union
        Map<Long, Map<String, Integer>> candidateTermFrequencies = new HashMap<>();
        for (String term : stemWeights.keySet()) {
            Optional<PostingsList> postingsOpt = snapshot.getPostings(term);
            if (postingsOpt.isEmpty()) {
                continue;
            }
            for (Posting posting : postingsOpt.get().getPostings()) {
                if (activeDocumentFilter != null && !activeDocumentFilter.isActive(posting.documentId())) {
                    continue;
                }
                candidateTermFrequencies
                        .computeIfAbsent(posting.documentId(), k -> new HashMap<>())
                        .put(term, posting.termFrequency());
            }
        }

        if (candidateTermFrequencies.isEmpty()) {
            return List.of();
        }

        // 4. Convert Candidates to DocumentStats (lengths retrieved from point-in-time snapshot)
        List<DocumentStats> candidateStats = new ArrayList<>(candidateTermFrequencies.size());
        for (Map.Entry<Long, Map<String, Integer>> entry : candidateTermFrequencies.entrySet()) {
            long docId = entry.getKey();
            int docLength = snapshot.getDocLength(docId);
            candidateStats.add(new DocumentStats(docId, docLength, entry.getValue()));
        }

        // 5. In-Memory Scoring with Synonym Weight Multipliers
        List<ScoredDocument> scoredDocuments = bm25Scorer.scoreDocuments(
                candidateStats,
                stemWeights.keySet(),
                stemWeights,
                collectionStats
        );

        // 6. Top-K Truncation and Ranked Result Packaging
        return packageTopKResults(scoredDocuments, limit);
    }

    private List<RankedSearchResult> searchStandard(String query, int limit) {
        // 1. Linguistic Query Processing
        List<ProcessedToken> tokens = tokenizer.tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> queryTerms = tokens.stream()
                .map(ProcessedToken::term)
                .toList();
        Set<String> uniqueTerms = new LinkedHashSet<>(queryTerms);

        // 2. Atomic Collection Statistics & Postings Snapshot Extraction
        IndexQuerySnapshot snapshot = invertedIndex.getSnapshot(uniqueTerms);
        long totalDocuments = snapshot.totalDocuments();
        if (totalDocuments <= 0) {
            return List.of();
        }
        double avgDocLength = snapshot.averageDocumentLength();

        Map<String, Long> dfMap = new HashMap<>();
        for (String term : uniqueTerms) {
            long df = snapshot.getPostings(term)
                    .map(pl -> (long) pl.size())
                    .orElse(0L);
            dfMap.put(term, df);
        }
        CollectionStats collectionStats = new CollectionStats(totalDocuments, avgDocLength, dfMap);

        // 3. Candidate Generation via Term-At-A-Time (TAAT) Union
        Map<Long, Map<String, Integer>> candidateTermFrequencies = new HashMap<>();
        for (String term : uniqueTerms) {
            Optional<PostingsList> postingsOpt = snapshot.getPostings(term);
            if (postingsOpt.isEmpty()) {
                continue;
            }
            for (Posting posting : postingsOpt.get().getPostings()) {
                if (activeDocumentFilter != null && !activeDocumentFilter.isActive(posting.documentId())) {
                    continue;
                }
                candidateTermFrequencies
                        .computeIfAbsent(posting.documentId(), k -> new HashMap<>())
                        .put(term, posting.termFrequency());
            }
        }

        if (candidateTermFrequencies.isEmpty()) {
            return List.of();
        }

        // 4. Convert Candidates to DocumentStats (lengths retrieved from point-in-time snapshot)
        List<DocumentStats> candidateStats = new ArrayList<>(candidateTermFrequencies.size());
        for (Map.Entry<Long, Map<String, Integer>> entry : candidateTermFrequencies.entrySet()) {
            long docId = entry.getKey();
            int docLength = snapshot.getDocLength(docId);
            candidateStats.add(new DocumentStats(docId, docLength, entry.getValue()));
        }

        // 5. In-Memory Scoring and Sorting
        List<ScoredDocument> scoredDocuments = bm25Scorer.scoreDocuments(
                candidateStats,
                queryTerms,
                collectionStats
        );

        // 6. Top-K Truncation and Ranked Result Packaging
        return packageTopKResults(scoredDocuments, limit);
    }

    private List<RankedSearchResult> packageTopKResults(List<ScoredDocument> scoredDocuments, int limit) {
        List<RankedSearchResult> results = new ArrayList<>();
        int rank = 1;
        for (ScoredDocument doc : scoredDocuments) {
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
}
