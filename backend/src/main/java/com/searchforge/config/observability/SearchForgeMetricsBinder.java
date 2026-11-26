package com.searchforge.config.observability;

import com.searchforge.indexer.service.IndexingEngine;
import com.searchforge.lifecycle.metrics.LifecycleMetricsCollector;
import com.searchforge.semantic.vector.VectorStorage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Binds SearchForge's internal index, vector, and lifecycle telemetry into Micrometer
 * for Actuator /metrics and /prometheus exposure without duplicating metric systems.
 */
@Component
public class SearchForgeMetricsBinder implements MeterBinder {

    private final IndexingEngine indexingEngine;
    private final VectorStorage vectorStorage;
    private final LifecycleMetricsCollector lifecycleMetricsCollector;

    public SearchForgeMetricsBinder(IndexingEngine indexingEngine,
                                    VectorStorage vectorStorage,
                                    LifecycleMetricsCollector lifecycleMetricsCollector) {
        this.indexingEngine = indexingEngine;
        this.vectorStorage = vectorStorage;
        this.lifecycleMetricsCollector = lifecycleMetricsCollector;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("searchforge.index.documents", indexingEngine,
                        engine -> engine.getStats().totalIndexedDocuments())
                .description("Total documents currently in the lexical inverted index")
                .register(registry);

        Gauge.builder("searchforge.index.terms", indexingEngine,
                        engine -> engine.getStats().totalTerms())
                .description("Total unique terms in the lexical inverted index")
                .register(registry);

        Gauge.builder("searchforge.vector.embeddings", vectorStorage, VectorStorage::size)
                .description("Total dense document embeddings in VectorStorage")
                .register(registry);

        Gauge.builder("searchforge.lifecycle.documents.failed", lifecycleMetricsCollector,
                        collector -> collector.getSnapshot().documentsFailed())
                .description("Cumulative failed document lifecycle operations")
                .register(registry);

        Gauge.builder("searchforge.lifecycle.documents.repaired", lifecycleMetricsCollector,
                        collector -> collector.getSnapshot().documentsRepaired())
                .description("Cumulative auto-healed document lifecycle operations")
                .register(registry);
    }
}
