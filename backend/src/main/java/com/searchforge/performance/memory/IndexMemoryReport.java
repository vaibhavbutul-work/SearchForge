package com.searchforge.performance.memory;

import java.io.Serializable;

/**
 * Domain representation of the SearchForge JVM and Index Memory Audit Report.
 *
 * Captures heap memory allocations, retained structure sizes, and per-document scaling metrics.
 */
public record IndexMemoryReport(
        int documentCount,
        int vocabularySize,
        long totalPostingsCount,
        long totalPositionsCount,
        long totalTokensCount,
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        long estimatedLexicalIndexBytes,
        long estimatedVectorStorageBytes,
        long estimatedTotalIndexBytes,
        double bytesPerDocument,
        double bytesPerPosting,
        double bytesPerTerm
) implements Serializable {

    public double heapUsedMb() {
        return (double) heapUsedBytes / (1024.0 * 1024.0);
    }

    public double heapCommittedMb() {
        return (double) heapCommittedBytes / (1024.0 * 1024.0);
    }

    public double estimatedLexicalMb() {
        return (double) estimatedLexicalIndexBytes / (1024.0 * 1024.0);
    }

    public double estimatedVectorMb() {
        return (double) estimatedVectorStorageBytes / (1024.0 * 1024.0);
    }

    public double estimatedTotalIndexMb() {
        return (double) estimatedTotalIndexBytes / (1024.0 * 1024.0);
    }

    public double kilobytesPerDocument() {
        return bytesPerDocument / 1024.0;
    }

    @Override
    public String toString() {
        return String.format(
                "IndexMemoryReport[docs=%d, vocab=%d, postings=%d, positions=%d, lexical=%.2f MB, vector=%.2f MB, totalIndex=%.2f MB (%.1f B/doc)]",
                documentCount, vocabularySize, totalPostingsCount, totalPositionsCount,
                estimatedLexicalMb(), estimatedVectorMb(), estimatedTotalIndexMb(), bytesPerDocument
        );
    }
}
