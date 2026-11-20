package com.searchforge.ranking.scorer;

import com.searchforge.ranking.model.CollectionStats;
import com.searchforge.ranking.model.DocumentStats;
import com.searchforge.ranking.model.ScoredDocument;
import com.searchforge.ranking.weight.IdfCalculator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service orchestrating document-level BM25 scoring for multi-term queries.
 *
 * Guarantees:
 * 1. Zero database or I/O calls inside scoring operations (pure in-memory calculation).
 * 2. Pre-computes query term IDFs once per search execution across all candidates.
 * 3. Provides term-by-term explainability breakdown in {@link ScoredDocument}.
 * 4. Produces naturally sorted results ordered by relevance descending with deterministic tie-breaking.
 */
@Service
public class Bm25Scorer {

    private final Bm25TermScorer termScorer;
    private final IdfCalculator idfCalculator;

    public Bm25Scorer(Bm25TermScorer termScorer,
                      @Qualifier("bm25IdfCalculator") IdfCalculator idfCalculator) {
        this.termScorer = Objects.requireNonNull(termScorer, "Bm25TermScorer cannot be null");
        this.idfCalculator = Objects.requireNonNull(idfCalculator, "IdfCalculator cannot be null");
    }

    /**
     * Pre-computes and caches IDF weights for all query terms using collection statistics.
     * Ensures IDF calculation is executed once per query term rather than repeated per document.
     */
    public Map<String, Double> computeQueryIdfs(Collection<String> queryTerms, CollectionStats collectionStats) {
        if (queryTerms == null || queryTerms.isEmpty() || collectionStats == null) {
            return Map.of();
        }

        Map<String, Double> idfMap = new HashMap<>();
        for (String term : queryTerms) {
            if (term != null && !term.isBlank()) {
                long df = collectionStats.getDocumentFrequency(term);
                double idf = idfCalculator.calculateIdf(df, collectionStats.totalDocuments());
                idfMap.put(term, idf);
            }
        }
        return Collections.unmodifiableMap(idfMap);
    }

    /**
     * Scores a single document against precomputed query term IDFs.
     *
     * @param docStats        Document statistics containing term frequencies and document length.
     * @param precomputedIdfs Map of query term to precomputed IDF weight.
     * @param avgDocLength    Average document length across the collection (avgdl).
     * @return ScoredDocument containing total score and term-level score breakdowns.
     */
    /**
     * Scores a single document against precomputed query term IDFs with optional term weights.
     *
     * @param docStats        Document statistics containing term frequencies and document length.
     * @param precomputedIdfs Map of query term to precomputed IDF weight.
     * @param avgDocLength    Average document length across the collection (avgdl).
     * @return ScoredDocument containing total score and term-level score breakdowns.
     */
    public ScoredDocument scoreDocument(DocumentStats docStats, Map<String, Double> precomputedIdfs, double avgDocLength) {
        return scoreDocument(docStats, precomputedIdfs, Map.of(), avgDocLength);
    }

    /**
     * Scores a single document against precomputed query term IDFs and explicit term weights.
     *
     * @param docStats        Document statistics containing term frequencies and document length.
     * @param precomputedIdfs Map of query term to precomputed IDF weight.
     * @param termWeights     Map of term to weight multiplier (defaults to 1.0 if not present).
     * @param avgDocLength    Average document length across the collection (avgdl).
     * @return ScoredDocument containing total score and term-level score breakdowns.
     */
    public ScoredDocument scoreDocument(DocumentStats docStats,
                                        Map<String, Double> precomputedIdfs,
                                        Map<String, Double> termWeights,
                                        double avgDocLength) {
        if (docStats == null) {
            throw new IllegalArgumentException("DocumentStats cannot be null");
        }
        if (precomputedIdfs == null || precomputedIdfs.isEmpty()) {
            return new ScoredDocument(docStats.docId(), 0.0, Map.of());
        }

        double totalScore = 0.0;
        Map<String, Double> termScores = new HashMap<>();

        for (Map.Entry<String, Double> entry : precomputedIdfs.entrySet()) {
            String term = entry.getKey();
            double idf = entry.getValue();

            if (idf <= 0.0) {
                continue;
            }

            int tf = docStats.getTermFrequency(term);
            if (tf <= 0) {
                continue;
            }

            double weight = termWeights != null ? termWeights.getOrDefault(term, 1.0) : 1.0;
            double termContribution = weight * termScorer.scoreTerm(tf, docStats.docLength(), avgDocLength, idf);
            if (termContribution > 0.0) {
                totalScore += termContribution;
                termScores.put(term, termContribution);
            }
        }

        return new ScoredDocument(docStats.docId(), totalScore, termScores);
    }

    /**
     * Convenience method to score a single document against query terms and collection statistics.
     */
    public ScoredDocument scoreDocument(DocumentStats docStats, List<String> queryTerms, CollectionStats collectionStats) {
        Map<String, Double> idfs = computeQueryIdfs(queryTerms, collectionStats);
        double avgDocLength = collectionStats != null ? collectionStats.avgDocLength() : 0.0;
        return scoreDocument(docStats, idfs, avgDocLength);
    }

    /**
     * Scores a batch of candidate documents against query terms and collection statistics.
     *
     * @param documents       Collection of candidate document statistics.
     * @param queryTerms      List of normalized/stemmed query terms.
     * @param collectionStats Collection statistics.
     * @return List of {@link ScoredDocument} sorted descending by score with ascending docId tie-breaking.
     */
    public List<ScoredDocument> scoreDocuments(Collection<DocumentStats> documents,
                                               List<String> queryTerms,
                                               CollectionStats collectionStats) {
        return scoreDocuments(documents, queryTerms, Map.of(), collectionStats);
    }

    /**
     * Scores a batch of candidate documents against query terms, collection statistics, and term weights.
     *
     * @param documents       Collection of candidate document statistics.
     * @param queryTerms      Collection of query terms.
     * @param termWeights     Map of term to weight multiplier (e.g. 1.0 for original, 0.5 for synonyms).
     * @param collectionStats Collection statistics.
     * @return List of {@link ScoredDocument} sorted descending by score with ascending docId tie-breaking.
     */
    public List<ScoredDocument> scoreDocuments(Collection<DocumentStats> documents,
                                               Collection<String> queryTerms,
                                               Map<String, Double> termWeights,
                                               CollectionStats collectionStats) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        Map<String, Double> idfs = computeQueryIdfs(queryTerms, collectionStats);
        double avgDocLength = collectionStats != null ? collectionStats.avgDocLength() : 0.0;

        return documents.stream()
                .map(doc -> scoreDocument(doc, idfs, termWeights, avgDocLength))
                .sorted()
                .toList();
    }
}
