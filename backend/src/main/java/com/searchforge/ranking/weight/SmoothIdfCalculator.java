package com.searchforge.ranking.weight;

import org.springframework.stereotype.Component;

/**
 * Classic Smoothed Vector Space Model (VSM) Inverse Document Frequency (IDF) calculator.
 *
 * Implements:
 * IDF_SMOOTH(t) = ln(1 + N / df(t))
 *
 * Provides a smooth logarithmic penalty for common terms while guaranteeing positive
 * weights, commonly used in traditional Vector Space Information Retrieval.
 */
@Component
public class SmoothIdfCalculator implements IdfCalculator {

    @Override
    public double calculateIdf(long documentFrequency, long totalDocuments) {
        if (totalDocuments <= 0 || documentFrequency <= 0) {
            return 0.0;
        }

        // Clamp df to totalDocuments to guard against inconsistent statistics
        long df = Math.min(documentFrequency, totalDocuments);

        double ratio = (double) totalDocuments / df;
        double idf = Math.log(1.0 + ratio);

        return Math.max(0.0, idf);
    }
}
