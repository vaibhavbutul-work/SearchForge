package com.searchforge.ranking.weight;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Robertson–Spärck Jones BM25 Inverse Document Frequency (IDF) implementation.
 *
 * Implements the strictly non-negative variant:
 * IDF_BM25(t) = ln(1 + (N - df(t) + 0.5) / (df(t) + 0.5))
 *
 * Guarantees that IDF is strictly non-negative for all 0 <= df <= N, avoiding
 * the classic negative IDF flaw where common terms penalize document relevance.
 */
@Component
@Primary
public class Bm25IdfCalculator implements IdfCalculator {

    @Override
    public double calculateIdf(long documentFrequency, long totalDocuments) {
        if (totalDocuments <= 0 || documentFrequency <= 0) {
            return 0.0;
        }

        // Clamp df to totalDocuments to guard against out-of-sync corpus anomalies
        long df = Math.min(documentFrequency, totalDocuments);

        double numerator = (totalDocuments - df) + 0.5;
        double denominator = df + 0.5;

        double fraction = numerator / denominator;
        double idf = Math.log(1.0 + fraction);

        return Math.max(0.0, idf);
    }
}
