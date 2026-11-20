package com.searchforge.semantic.tokenizer;

/**
 * Exception thrown when tokenization or vocabulary loading fails.
 */
public class TokenizerException extends RuntimeException {

    public TokenizerException(String message) {
        super(message);
    }

    public TokenizerException(String message, Throwable cause) {
        super(message, cause);
    }
}
