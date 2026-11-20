package com.searchforge.semantic.embedding;

import com.searchforge.semantic.embedding.exception.ModelInitializationException;
import com.searchforge.semantic.tokenizer.Tokenizer;
import com.searchforge.semantic.tokenizer.WordPieceTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Spring configuration factory for semantic embedding models and tokenizers.
 *
 * Provides managed, reusable {@link Tokenizer} and {@link EmbeddingModel} singleton instances.
 * Guarantees explicit observability: clearly logs the active provider and prevents silent,
 * unintended degradation from ONNX to deterministic test mode in production.
 */
@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    private final EmbeddingProperties properties;

    public EmbeddingConfig(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @Bean("wordPieceTokenizer")
    @ConditionalOnMissingBean(name = "wordPieceTokenizer")
    public Tokenizer wordPieceTokenizer() {
        String vocabPath = properties.getTokenizerPath();
        if (vocabPath == null || vocabPath.isBlank()) {
            vocabPath = "classpath:models/vocab.txt";
        }
        return new WordPieceTokenizer(vocabPath);
    }

    @Bean
    @ConditionalOnMissingBean(QueryEmbeddingCache.class)
    public QueryEmbeddingCache queryEmbeddingCache() {
        int capacity = properties.getCacheCapacity() > 0 ? properties.getCacheCapacity() : 1000;
        return new QueryEmbeddingCache(capacity);
    }

    public EmbeddingModel embeddingModel() {
        return embeddingModel(wordPieceTokenizer());
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel embeddingModel(Tokenizer wordPieceTokenizer) {
        if ("onnx".equalsIgnoreCase(properties.getModelType())) {
            String onnxPath = properties.resolveEffectiveModelPath();
            if (onnxPath != null && !onnxPath.isBlank() && new File(onnxPath).exists()) {
                log.info("================================================================================");
                log.info("SearchForge Active Embedding Provider: ONNX RUNTIME");
                log.info("  Model: {}, Dimensions: {}, Path: {}", properties.getModelName(), properties.getDimensions(), onnxPath);
                log.info("  Vocabulary: {} (tokens: {})", properties.getTokenizerPath(), wordPieceTokenizer.getVocabSize());
                log.info("================================================================================");
                try {
                    return new OnnxEmbeddingModel(
                            onnxPath,
                            wordPieceTokenizer,
                            properties.getDimensions(),
                            properties.getMaxSequenceLength()
                    );
                } catch (Exception e) {
                    log.error("Failed to initialize ONNX embedding model from path: {}", onnxPath, e);
                    throw new ModelInitializationException("Failed to initialize ONNX model: " + e.getMessage(), e);
                }
            } else {
                if (properties.isAllowFallbackToDeterministic()) {
                    log.warn("ONNX model configured but not found at '{}'. Degrading to DETERMINISTIC TEST provider.", onnxPath);
                } else {
                    throw new ModelInitializationException(
                            "ONNX embedding model requested ('semantic.embedding.model-type=onnx') but model file was not found. " +
                            "Checked configured path: '" + properties.getOnnxModelPath() + "', " +
                            "and default cache: '" + onnxPath + "'. " +
                            "To enable offline tests without ONNX models, set 'semantic.embedding.model-type=deterministic'."
                    );
                }
            }
        }

        log.info("================================================================================");
        log.info("SearchForge Active Embedding Provider: DETERMINISTIC_HASH_TEST");
        log.info("  NOTE: This provider uses deterministic term-hash projections for hermetic testing.");
        log.info("  To use real transformer neural embeddings, configure 'semantic.embedding.model-type=onnx'.");
        log.info("================================================================================");
        return new DeterministicEmbeddingModel(properties.getDimensions(), properties.getMaxSequenceLength());
    }
}
