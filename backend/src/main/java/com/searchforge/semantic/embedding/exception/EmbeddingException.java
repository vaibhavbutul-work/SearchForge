package com.searchforge.semantic.embedding.exception;

/**
 * Base runtime exception thrown when embedding generation or vector inference fails.
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
