package com.searchforge.config.observability;

import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.indexer.service.IndexingStats;
import com.searchforge.semantic.vector.VectorStorage;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness health indicator reporting in-memory lexical index and vector store metrics.
 */
@Component("searchIndex")
public class SearchIndexHealthIndicator implements HealthIndicator {

    private final IndexingEngine indexingEngine;
    private final VectorStorage vectorStorage;

    public SearchIndexHealthIndicator(IndexingEngine indexingEngine, VectorStorage vectorStorage) {
        this.indexingEngine = indexingEngine;
        this.vectorStorage = vectorStorage;
    }

    @Override
    public Health health() {
        try {
            IndexingStats stats = indexingEngine.getStats();
            int vectorCount = vectorStorage.size();
            return Health.up()
                    .withDetail("indexedDocuments", stats.totalIndexedDocuments())
                    .withDetail("uniqueTerms", stats.totalTerms())
                    .withDetail("totalPostings", stats.totalPostings())
                    .withDetail("storedVectors", vectorCount)
                    .build();
        } catch (Exception ex) {
            return Health.down(ex)
                    .withDetail("reason", "Failed to query search index readiness")
                    .build();
        }
    }
}
