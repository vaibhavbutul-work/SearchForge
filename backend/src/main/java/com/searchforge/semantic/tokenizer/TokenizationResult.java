package com.searchforge.semantic.tokenizer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable tokenization output containing subword tokens, numerical token IDs,
 * attention masks, token type IDs, and sequence metadata.
 */
public record TokenizationResult(
        List<String> tokens,
        long[] tokenIds,
        long[] attentionMask,
        long[] tokenTypeIds,
        int sequenceLength,
        boolean truncated
) {

    public TokenizationResult {
        Objects.requireNonNull(tokens, "tokens cannot be null");
        Objects.requireNonNull(tokenIds, "tokenIds cannot be null");
        Objects.requireNonNull(attentionMask, "attentionMask cannot be null");
        Objects.requireNonNull(tokenTypeIds, "tokenTypeIds cannot be null");

        tokens = Collections.unmodifiableList(List.copyOf(tokens));
        tokenIds = Arrays.copyOf(tokenIds, tokenIds.length);
        attentionMask = Arrays.copyOf(attentionMask, attentionMask.length);
        tokenTypeIds = Arrays.copyOf(tokenTypeIds, tokenTypeIds.length);

        if (tokenIds.length != attentionMask.length || tokenIds.length != tokenTypeIds.length) {
            throw new IllegalArgumentException(String.format(
                    "Tensor array lengths mismatch: tokenIds=%d, attentionMask=%d, tokenTypeIds=%d",
                    tokenIds.length, attentionMask.length, tokenTypeIds.length));
        }
    }

    @Override
    public long[] tokenIds() {
        return Arrays.copyOf(tokenIds, tokenIds.length);
    }

    @Override
    public long[] attentionMask() {
        return Arrays.copyOf(attentionMask, attentionMask.length);
    }

    @Override
    public long[] tokenTypeIds() {
        return Arrays.copyOf(tokenTypeIds, tokenTypeIds.length);
    }
}
