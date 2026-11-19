package com.searchforge.ranking.retrieval;

import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;
import com.searchforge.ranking.model.CollectionStats;
import com.searchforge.ranking.model.DocumentStats;
import com.searchforge.ranking.model.ScoredDocument;
import com.searchforge.ranking.scorer.Bm25Scorer;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Production implementation of {@link RankedRetrievalEngine} executing free-text BM25 search.
 *
 * Implements the standard information retrieval pipeline:
 * 1. Linguistic query processing (normalization, tokenization, stop-word removal, stemming).
 * 2. In-memory collection statistics extraction from {@link InvertedIndex}.
 * 3. Fast Term-At-A-Time (TAAT) candidate generation via posting list union.
 * 4. Pure in-memory BM25 scoring with zero database calls.
 * 5. Top-K truncation and ranked result packaging.
 */
@Service
public class DefaultRankedRetrievalEngine implements RankedRetrievalEngine {

    private final InvertedIndex invertedIndex;
    private final Bm25Scorer bm25Scorer;
    private final Tokenizer tokenizer;

    public DefaultRankedRetrievalEngine(InvertedIndex invertedIndex,
                                        Bm25Scorer bm25Scorer,
                                        Tokenizer tokenizer) {
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "InvertedIndex cannot be null");
        this.bm25Scorer = Objects.requireNonNull(bm25Scorer, "Bm25Scorer cannot be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");
    }

    @Override
    public List<RankedSearchResult> search(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        // 1. Linguistic Query Processing
        List<ProcessedToken> tokens = tokenizer.tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<String> queryTerms = tokens.stream()
                .map(ProcessedToken::term)
                .toList();
        Set<String> uniqueTerms = new LinkedHashSet<>(queryTerms);

        // 2. Collection Statistics Extraction
        long totalDocuments = invertedIndex.getTotalDocuments();
        if (totalDocuments <= 0) {
            return List.of();
        }
        double avgDocLength = invertedIndex.getAverageDocumentLength();

        Map<String, Long> dfMap = new HashMap<>();
        for (String term : uniqueTerms) {
            long df = invertedIndex.getPostings(term)
                    .map(PostingsList::size)
                    .orElse(0);
            dfMap.put(term, df);
        }
        CollectionStats collectionStats = new CollectionStats(totalDocuments, avgDocLength, dfMap);

        // 3. Candidate Generation via Term-At-A-Time (TAAT) Union
        Map<Long, Map<String, Integer>> candidateTermFrequencies = new HashMap<>();
        for (String term : uniqueTerms) {
            Optional<PostingsList> postingsOpt = invertedIndex.getPostings(term);
            if (postingsOpt.isEmpty()) {
                continue;
            }
            for (Posting posting : postingsOpt.get().getPostings()) {
                candidateTermFrequencies
                        .computeIfAbsent(posting.documentId(), k -> new HashMap<>())
                        .put(term, posting.termFrequency());
            }
        }

        if (candidateTermFrequencies.isEmpty()) {
            return List.of();
        }

        // 4. Convert Candidates to DocumentStats
        List<DocumentStats> candidateStats = new ArrayList<>(candidateTermFrequencies.size());
        for (Map.Entry<Long, Map<String, Integer>> entry : candidateTermFrequencies.entrySet()) {
            long docId = entry.getKey();
            int docLength = invertedIndex.getDocLength(docId);
            candidateStats.add(new DocumentStats(docId, docLength, entry.getValue()));
        }

        // 5. In-Memory Scoring and Sorting
        List<ScoredDocument> scoredDocuments = bm25Scorer.scoreDocuments(
                candidateStats,
                queryTerms,
                collectionStats
        );

        // 6. Top-K Truncation and Ranked Result Packaging
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
