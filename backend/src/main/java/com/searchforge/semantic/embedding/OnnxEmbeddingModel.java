package com.searchforge.semantic.embedding;

import ai.onnxruntime.*;
import com.searchforge.semantic.embedding.exception.EmbeddingException;
import com.searchforge.semantic.embedding.exception.ModelInitializationException;
import com.searchforge.semantic.tokenizer.TokenizationResult;
import com.searchforge.semantic.tokenizer.Tokenizer;
import com.searchforge.semantic.tokenizer.WordPieceTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Production implementation of {@link EmbeddingModel} executing local transformer models
 * via Microsoft ONNX Runtime (e.g. all-MiniLM-L6-v2).
 *
 * Implements:
 * 1. Model-compliant vocabulary WordPiece tokenization.
 * 2. Dynamic sequence length allocation without unnecessary 256-token padding.
 * 3. Mean Pooling over valid token hidden states weighted by attention mask.
 * 4. L2 Unit Normalization.
 */
public class OnnxEmbeddingModel implements EmbeddingModel, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingModel.class);

    private final String modelPath;
    private final int dimensions;
    private final int maxSequenceLength;
    private final Tokenizer tokenizer;
    private final OrtEnvironment environment;
    private final OrtSession session;

    public OnnxEmbeddingModel(String modelPath, Tokenizer tokenizer, int dimensions, int maxSequenceLength) {
        if (modelPath == null || modelPath.isBlank()) {
            throw new ModelInitializationException("Model path cannot be null or empty");
        }
        File file = new File(modelPath);
        if (!file.exists() || !file.isFile()) {
            throw new ModelInitializationException("ONNX model file does not exist at: " + modelPath);
        }

        this.modelPath = modelPath;
        this.dimensions = dimensions > 0 ? dimensions : 384;
        this.maxSequenceLength = maxSequenceLength > 0 ? maxSequenceLength : 256;
        this.tokenizer = Objects.requireNonNull(tokenizer, "Tokenizer cannot be null");

        try {
            this.environment = OrtEnvironment.getEnvironment("SearchForgeEmbeddingEnv");
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            // Use reasonable thread count to avoid CPU thread oversubscription
            int cpuCores = Runtime.getRuntime().availableProcessors();
            sessionOptions.setIntraOpNumThreads(Math.max(1, Math.min(4, cpuCores / 2)));
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            sessionOptions.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            this.session = environment.createSession(modelPath, sessionOptions);
            log.info("Successfully loaded ONNX embedding model from {} (dimensions: {}, maxSeqLen: {}, vocabSize: {})",
                    modelPath, this.dimensions, this.maxSequenceLength, tokenizer.getVocabSize());
            warmup();
        } catch (Exception e) {
            throw new ModelInitializationException("Failed to initialize ONNX Runtime session: " + e.getMessage(), e);
        }
    }

    public OnnxEmbeddingModel(String modelPath, String tokenizerPath, int dimensions, int maxSequenceLength) {
        this(
                modelPath,
                new WordPieceTokenizer(tokenizerPath != null && !tokenizerPath.isBlank() ? tokenizerPath : "default"),
                dimensions,
                maxSequenceLength
        );
    }

    @Override
    public float[] embed(String text) {
        validateInput(text);
        List<float[]> batch = embedBatch(List.of(text));
        return batch.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null) {
            throw new IllegalArgumentException("Batch input texts cannot be null");
        }
        if (texts.isEmpty()) {
            return Collections.emptyList();
        }

        int batchSize = texts.size();
        for (int i = 0; i < batchSize; i++) {
            if (texts.get(i) == null || texts.get(i).isBlank()) {
                throw new IllegalArgumentException("Batch text at index " + i + " cannot be null or blank");
            }
        }

        // 1. Tokenize inputs dynamically using WordPiece vocabulary
        List<TokenizationResult> tokenizedList = new ArrayList<>(batchSize);
        int batchMaxSeqLen = 2; // minimum [CLS] + [SEP]

        for (String text : texts) {
            TokenizationResult res = tokenizer.tokenize(text, maxSequenceLength);
            tokenizedList.add(res);
            batchMaxSeqLen = Math.max(batchMaxSeqLen, res.sequenceLength());
        }

        // 2. Allocate dynamic tensor buffers of shape [batchSize, batchMaxSeqLen]
        long[][] inputIds = new long[batchSize][batchMaxSeqLen];
        long[][] attentionMask = new long[batchSize][batchMaxSeqLen];
        long[][] tokenTypeIds = new long[batchSize][batchMaxSeqLen];

        for (int b = 0; b < batchSize; b++) {
            TokenizationResult res = tokenizedList.get(b);
            long[] ids = res.tokenIds();
            int len = ids.length;

            for (int i = 0; i < len; i++) {
                inputIds[b][i] = ids[i];
                attentionMask[b][i] = 1L;
                tokenTypeIds[b][i] = 0L;
            }

            // Pad remaining positions up to batchMaxSeqLen (if batch contains unequal length inputs)
            for (int i = len; i < batchMaxSeqLen; i++) {
                inputIds[b][i] = tokenizer.getPadTokenId();
                attentionMask[b][i] = 0L;
                tokenTypeIds[b][i] = 0L;
            }
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            inputs.put("input_ids", OnnxTensor.createTensor(environment, inputIds));
            inputs.put("attention_mask", OnnxTensor.createTensor(environment, attentionMask));

            // Include token_type_ids if expected by model inputs
            if (session.getInputNames().contains("token_type_ids")) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(environment, tokenTypeIds));
            }

            try (OrtSession.Result results = session.run(inputs)) {
                // Find token embeddings tensor (typically named 'last_hidden_state' or index 0)
                OnnxValue outputVal = results.get(0);
                if (!(outputVal instanceof OnnxTensor outputTensor)) {
                    throw new EmbeddingException("Expected OnnxTensor output from model, got: " + outputVal.getClass().getName());
                }

                // Tensor shape: [batch_size, sequence_length, hidden_dim]
                float[][][] tokenEmbeddings = (float[][][]) outputTensor.getValue();
                List<float[]> embeddings = new ArrayList<>(batchSize);

                for (int b = 0; b < batchSize; b++) {
                    float[] pooled = meanPoolAndNormalize(tokenEmbeddings[b], attentionMask[b]);
                    validateOutput(pooled);
                    embeddings.add(pooled);
                }

                return Collections.unmodifiableList(embeddings);
            }
        } catch (OrtException e) {
            throw new EmbeddingException("ONNX Runtime inference error: " + e.getMessage(), e);
        } finally {
            for (OnnxTensor tensor : inputs.values()) {
                tensor.close();
            }
        }
    }

    /**
     * Performs Mean Pooling across valid token hidden states weighted by attention mask,
     * followed by L2 Unit Normalization.
     */
    private float[] meanPoolAndNormalize(float[][] tokenEmbeddings, long[] mask) {
        float[] pooled = new float[dimensions];
        float sumWeights = 0.0f;

        int seqLen = Math.min(tokenEmbeddings.length, mask.length);
        for (int i = 0; i < seqLen; i++) {
            if (mask[i] == 1L) {
                sumWeights += 1.0f;
                int tokenDim = Math.min(tokenEmbeddings[i].length, dimensions);
                for (int d = 0; d < tokenDim; d++) {
                    pooled[d] += tokenEmbeddings[i][d];
                }
            }
        }

        if (sumWeights > 0.0f) {
            float invSum = 1.0f / sumWeights;
            for (int d = 0; d < dimensions; d++) {
                pooled[d] *= invSum;
            }
        }

        // L2 Unit Normalization
        double sumSq = 0.0;
        for (float val : pooled) {
            sumSq += (double) val * val;
        }
        double norm = Math.sqrt(sumSq);
        if (norm > 1e-9) {
            float invNorm = (float) (1.0 / norm);
            for (int d = 0; d < dimensions; d++) {
                pooled[d] *= invNorm;
            }
        }

        return pooled;
    }

    private void validateInput(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Input text cannot be null or blank");
        }
    }

    private void validateOutput(float[] vector) {
        if (vector.length != dimensions) {
            throw new EmbeddingException("Vector dimension mismatch: expected " + dimensions + " but got " + vector.length);
        }
        for (int i = 0; i < vector.length; i++) {
            if (Float.isNaN(vector[i]) || Float.isInfinite(vector[i])) {
                throw new EmbeddingException("Vector contains NaN or Infinite value at index " + i);
            }
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return "onnx-" + new File(modelPath).getName();
    }

    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    /**
     * Executes a preliminary inference to prime ONNX Runtime execution graphs,
     * thread pools, and memory arenas, eliminating first-query latency spikes.
     */
    public void warmup() {
        try {
            embed("searchforge semantic model warmup");
            log.info("ONNX embedding model execution graph successfully warmed up");
        } catch (Exception e) {
            log.warn("ONNX model warmup encountered non-fatal error: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (environment != null) {
                environment.close();
            }
            log.info("Closed ONNX Runtime session and environment cleanly.");
        } catch (Exception e) {
            log.warn("Error while closing ONNX Runtime resources: {}", e.getMessage());
        }
    }
}
