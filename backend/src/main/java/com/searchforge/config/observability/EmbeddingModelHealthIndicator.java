package com.searchforge.config.observability;

import com.searchforge.semantic.embedding.DeterministicEmbeddingModel;
import com.searchforge.semantic.embedding.EmbeddingModel;
import com.searchforge.semantic.embedding.EmbeddingProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator reporting active semantic embedding model status.
 * Reports UP (with mode details) when deterministic fallback is allowed, so optional
 * ONNX weights do not unnecessarily terminate container liveness.
 */
@Component("embeddingModelHealth")
public class EmbeddingModelHealthIndicator implements HealthIndicator {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties embeddingProperties;

    public EmbeddingModelHealthIndicator(EmbeddingModel embeddingModel, EmbeddingProperties embeddingProperties) {
        this.embeddingModel = embeddingModel;
        this.embeddingProperties = embeddingProperties;
    }

    @Override
    public Health health() {
        if (!embeddingProperties.isEnabled()) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("mode", "disabled")
                    .build();
        }

        if (embeddingModel == null) {
            return Health.down()
                    .withDetail("reason", "No EmbeddingModel bean available")
                    .build();
        }

        boolean isFallback = embeddingModel instanceof DeterministicEmbeddingModel;
        String mode = isFallback ? "deterministic-fallback" : "onnx";

        if (isFallback && "onnx".equalsIgnoreCase(embeddingProperties.getModelType())
                && !embeddingProperties.isAllowFallbackToDeterministic()) {
            return Health.down()
                    .withDetail("mode", mode)
                    .withDetail("modelName", embeddingModel.modelName())
                    .withDetail("reason", "ONNX model required but deterministic fallback is active")
                    .build();
        }

        return Health.up()
                .withDetail("enabled", true)
                .withDetail("mode", mode)
                .withDetail("modelName", embeddingModel.modelName())
                .withDetail("dimensions", embeddingModel.dimensions())
                .build();
    }
}
