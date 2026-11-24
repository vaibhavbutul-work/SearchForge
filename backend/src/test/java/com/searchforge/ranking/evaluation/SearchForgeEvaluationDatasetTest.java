package com.searchforge.ranking.evaluation;

import com.searchforge.crawler.model.CrawledDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Task 12.1 — Search Quality Evaluation Dataset & Relevance Judgments Suite")
class SearchForgeEvaluationDatasetTest {

    @Test
    @DisplayName("Corpus Completeness: Exactly 120 unique documents with valid metadata")
    void testCorpusCompleteness() {
        List<CrawledDocument> corpus = SearchForgeEvaluationCorpus.getCorpus();
        assertNotNull(corpus, "Corpus must not be null");
        assertEquals(120, corpus.size(), "Corpus must contain exactly 120 documents");
        assertEquals(120, SearchForgeEvaluationCorpus.size());

        Set<Long> docIds = new HashSet<>();
        for (CrawledDocument doc : corpus) {
            assertNotNull(doc, "Corpus document cannot be null");
            assertNotNull(doc.getId(), "Document ID cannot be null");
            assertTrue(doc.getId() >= 101L && doc.getId() <= 220L, "Doc ID must be in range [101, 220]: " + doc.getId());
            assertTrue(docIds.add(doc.getId()), "Doc ID must be unique: " + doc.getId());

            assertNotNull(doc.getTitle(), "Document title cannot be null for doc " + doc.getId());
            assertFalse(doc.getTitle().isBlank(), "Document title cannot be blank for doc " + doc.getId());

            assertNotNull(doc.getCleanText(), "Clean text cannot be null for doc " + doc.getId());
            assertFalse(doc.getCleanText().isBlank(), "Clean text cannot be blank for doc " + doc.getId());

            assertNotNull(doc.getUrl(), "Document URL cannot be null for doc " + doc.getId());
            assertFalse(doc.getUrl().isBlank(), "Document URL cannot be blank for doc " + doc.getId());

            assertTrue(SearchForgeEvaluationCorpus.containsDocument(doc.getId()));
            assertTrue(SearchForgeEvaluationCorpus.getDocument(doc.getId()).isPresent());
        }
    }

    @Test
    @DisplayName("Query Completeness: Exactly 48 queries across all 8 Information Retrieval archetypes")
    void testDatasetSizeAndArchetypeBalance() {
        List<EvaluationQueryV2> queries = SearchForgeEvaluationDataset.getAllQueries();
        assertNotNull(queries);
        assertEquals(48, queries.size(), "Dataset must contain exactly 48 queries");
        assertEquals(48, SearchForgeEvaluationDataset.size());

        // Check all 8 required categories have exactly 6 queries each
        EvaluationCategory[] requiredCategories = {
                EvaluationCategory.EXACT_KEYWORD,
                EvaluationCategory.BROAD_TOPICAL,
                EvaluationCategory.CONCEPTUAL,
                EvaluationCategory.MULTI_CONCEPT,
                EvaluationCategory.AMBIGUOUS,
                EvaluationCategory.NATURAL_LANGUAGE,
                EvaluationCategory.RARE_TERM,
                EvaluationCategory.SYNONYM
        };

        for (EvaluationCategory category : requiredCategories) {
            List<EvaluationQueryV2> catQueries = SearchForgeEvaluationDataset.getQueriesByCategory(category);
            assertEquals(6, catQueries.size(), "Category " + category + " must contain exactly 6 queries");
        }
    }

    @Test
    @DisplayName("Split Integrity & Zero Leakage: Strict 50% DEV, 25% TUNING, 25% EVAL with zero query overlap")
    void testSplitSizesAndZeroLeakage() {
        List<EvaluationQueryV2> devQueries = SearchForgeEvaluationDataset.getQueriesBySplit(EvaluationDatasetSplit.DEV);
        List<EvaluationQueryV2> tuningQueries = SearchForgeEvaluationDataset.getQueriesBySplit(EvaluationDatasetSplit.TUNING);
        List<EvaluationQueryV2> evalQueries = SearchForgeEvaluationDataset.getQueriesBySplit(EvaluationDatasetSplit.EVAL);

        assertEquals(24, devQueries.size(), "DEV split must contain 24 queries (50%)");
        assertEquals(12, tuningQueries.size(), "TUNING split must contain 12 queries (25%)");
        assertEquals(12, evalQueries.size(), "EVAL split must contain 12 queries (25%)");

        // Verify zero query ID overlap
        Set<String> devIds = devQueries.stream().map(EvaluationQueryV2::queryId).collect(Collectors.toSet());
        Set<String> tuningIds = tuningQueries.stream().map(EvaluationQueryV2::queryId).collect(Collectors.toSet());
        Set<String> evalIds = evalQueries.stream().map(EvaluationQueryV2::queryId).collect(Collectors.toSet());

        Set<String> devTuningOverlap = new HashSet<>(devIds);
        devTuningOverlap.retainAll(tuningIds);
        assertTrue(devTuningOverlap.isEmpty(), "Zero ID overlap between DEV and TUNING: " + devTuningOverlap);

        Set<String> devEvalOverlap = new HashSet<>(devIds);
        devEvalOverlap.retainAll(evalIds);
        assertTrue(devEvalOverlap.isEmpty(), "Zero ID overlap between DEV and EVAL: " + devEvalOverlap);

        Set<String> tuningEvalOverlap = new HashSet<>(tuningIds);
        tuningEvalOverlap.retainAll(evalIds);
        assertTrue(tuningEvalOverlap.isEmpty(), "Zero ID overlap between TUNING and EVAL: " + tuningEvalOverlap);

        // Verify zero normalized query string overlap
        Set<String> devTexts = devQueries.stream().map(q -> q.queryText().toLowerCase().trim()).collect(Collectors.toSet());
        Set<String> tuningTexts = tuningQueries.stream().map(q -> q.queryText().toLowerCase().trim()).collect(Collectors.toSet());
        Set<String> evalTexts = evalQueries.stream().map(q -> q.queryText().toLowerCase().trim()).collect(Collectors.toSet());

        Set<String> textOverlap1 = new HashSet<>(devTexts);
        textOverlap1.retainAll(tuningTexts);
        assertTrue(textOverlap1.isEmpty(), "Zero query text overlap between DEV and TUNING: " + textOverlap1);

        Set<String> textOverlap2 = new HashSet<>(devTexts);
        textOverlap2.retainAll(evalTexts);
        assertTrue(textOverlap2.isEmpty(), "Zero query text overlap between DEV and EVAL: " + textOverlap2);

        Set<String> textOverlap3 = new HashSet<>(tuningTexts);
        textOverlap3.retainAll(evalTexts);
        assertTrue(textOverlap3.isEmpty(), "Zero query text overlap between TUNING and EVAL: " + textOverlap3);
    }

    @Test
    @DisplayName("Relevance Judgments Integrity: All referenced document IDs exist in corpus and grades in [1, 3]")
    void testRelevanceJudgmentsReferencedDocsExist() {
        for (EvaluationQueryV2 q : SearchForgeEvaluationDataset.getAllQueries()) {
            assertFalse(q.groundTruth().isEmpty(), "Query " + q.queryId() + " must have non-empty ground truth");

            boolean hasPrimaryTarget = false;
            for (Map.Entry<Long, Integer> entry : q.groundTruth().entrySet()) {
                long docId = entry.getKey();
                int grade = entry.getValue();

                assertTrue(SearchForgeEvaluationCorpus.containsDocument(docId),
                        "Query " + q.queryId() + " references non-existent docId " + docId);

                assertTrue(grade >= 1 && grade <= 3,
                        "Query " + q.queryId() + " has invalid grade " + grade + " for doc " + docId);

                if (grade == 3) {
                    hasPrimaryTarget = true;
                    assertTrue(q.isHighlyRelevant(docId));
                }
                if (grade >= 2) {
                    assertTrue(q.isRelevant(docId));
                }
            }

            assertTrue(hasPrimaryTarget, "Query " + q.queryId() + " must have at least one Grade 3 primary target");
            assertTrue(q.countTotalRelevant() >= 1, "Query " + q.queryId() + " must have at least 1 relevant document (grade >= 2)");
        }
    }

    @Test
    @DisplayName("Documentation Integrity: Every query has non-blank intent rationale")
    void testQueryIntentRationalesNonEmpty() {
        for (EvaluationQueryV2 q : SearchForgeEvaluationDataset.getAllQueries()) {
            assertNotNull(q.rationale(), "Rationale cannot be null for " + q.queryId());
            assertFalse(q.rationale().isBlank(), "Rationale cannot be blank for " + q.queryId());
            assertTrue(q.rationale().length() >= 15, "Rationale must be informative for " + q.queryId());
        }
    }

    @Test
    @DisplayName("Interoperability: EvaluationQueryV2 converts accurately to legacy EvaluationQuery")
    void testBackwardCompatibilityConversion() {
        EvaluationQueryV2 v2 = SearchForgeEvaluationDataset.getQueryById("Q01").orElseThrow();
        EvaluationQuery legacy = v2.toEvaluationQuery();

        assertNotNull(legacy);
        assertEquals(v2.queryText(), legacy.query());
        assertEquals(v2.groundTruth(), legacy.groundTruth());
        assertEquals(3, legacy.getGrade(101L));
        assertEquals(2, legacy.getGrade(104L));
        assertEquals(0, legacy.getGrade(999L));
    }
}
