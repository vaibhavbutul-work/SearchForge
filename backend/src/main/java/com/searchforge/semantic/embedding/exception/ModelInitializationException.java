package com.searchforge.semantic.embedding.exception;

/**
 * Thrown when an embedding model or runtime engine fails to initialize or load required assets.
 */
public class ModelInitializationException extends EmbeddingException {

    public ModelInitializationException(String message) {
        super(message);
    }

    public ModelInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
