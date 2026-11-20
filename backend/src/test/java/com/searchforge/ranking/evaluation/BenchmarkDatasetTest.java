package com.searchforge.ranking.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BenchmarkDataset Unit Tests")
class BenchmarkDatasetTest {

    @Test
    @DisplayName("Controlled dataset contains all eight required Information Retrieval query categories")
    void getStandardDataset_shouldCoverAllEightCategories() {
        List<CategorizedEvaluationQuery> dataset = BenchmarkDataset.getStandardDataset();
        assertNotNull(dataset);
        assertFalse(dataset.isEmpty());
        assertTrue(dataset.size() >= 20, "Dataset should have at least 20 queries, got: " + dataset.size());

        Map<QueryCategory, Long> categoryCounts = dataset.stream()
                .collect(Collectors.groupingBy(CategorizedEvaluationQuery::category, Collectors.counting()));

        for (QueryCategory cat : QueryCategory.values()) {
            assertTrue(categoryCounts.containsKey(cat), "Dataset missing category: " + cat);
            assertTrue(categoryCounts.get(cat) >= 2, "Category " + cat + " should have at least 2 queries");
        }
    }

    @Test
    @DisplayName("Every benchmark query has valid ground truth judgments and positive grades")
    void getStandardDataset_eachQueryMustHaveGroundTruth() {
        List<CategorizedEvaluationQuery> dataset = BenchmarkDataset.getStandardDataset();

        for (CategorizedEvaluationQuery q : dataset) {
            assertNotNull(q.query());
            assertFalse(q.query().isBlank());
            assertFalse(q.groundTruth().isEmpty(), "Query must have ground truth judgments: " + q.query());
            assertTrue(q.rationale().length() > 5, "Query must have documented rationale: " + q.query());

            for (Map.Entry<Long, Integer> entry : q.groundTruth().entrySet()) {
                assertTrue(entry.getKey() > 0, "Document ID must be positive");
                assertTrue(entry.getValue() > 0, "Grade must be positive (>= 1)");
            }
        }
    }
}
