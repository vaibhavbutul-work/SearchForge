package com.searchforge.semantic.tokenizer;

/**
 * Contract for text tokenization compatible with transformer embedding models.
 *
 * Converts natural language text into subword token sequences and pre-trained vocabulary token IDs.
 */
public interface Tokenizer {

    /**
     * Tokenizes text using default maximum sequence length (typically 256 or 512).
     *
     * @param text input text string
     * @return {@link TokenizationResult} containing tokens, IDs, masks, and metadata
     */
    TokenizationResult tokenize(String text);

    /**
     * Tokenizes text enforcing a maximum sequence length, adding [CLS] and [SEP] special tokens,
     * and truncating if necessary.
     *
     * @param text input text string
     * @param maxSequenceLength maximum total tokens including [CLS] and [SEP]
     * @return {@link TokenizationResult}
     */
    TokenizationResult tokenize(String text, int maxSequenceLength);

    /**
     * Total number of unique tokens in the loaded vocabulary.
     */
    int getVocabSize();

    /**
     * Vocabulary ID for the padding token [PAD] (typically 0).
     */
    int getPadTokenId();

    /**
     * Vocabulary ID for the classification token [CLS] (typically 101).
     */
    int getClsTokenId();

    /**
     * Vocabulary ID for the separator token [SEP] (typically 102).
     */
    int getSepTokenId();

    /**
     * Vocabulary ID for the out-of-vocabulary unknown token [UNK] (typically 100).
     */
    int getUnkTokenId();

    /**
     * Converts a single token string to its vocabulary ID, or returns [UNK] ID if not found.
     */
    long convertTokenToId(String token);

    /**
     * Converts a vocabulary ID to its string token representation, or returns null if out of range.
     */
    String convertIdToToken(long id);
}
