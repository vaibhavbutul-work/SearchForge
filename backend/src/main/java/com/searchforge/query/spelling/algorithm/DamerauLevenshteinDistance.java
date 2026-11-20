package com.searchforge.query.spelling.algorithm;

/**
 * Implementation of the Damerau-Levenshtein distance algorithm (Optimal String Alignment).
 *
 * Computes the minimum number of single-character operations required to transform
 * one string into another, supporting four primitive edit operations:
 * 1. Insertion (e.g., "btre" -> "btree")
 * 2. Deletion (e.g., "javva" -> "java")
 * 3. Substitution (e.g., "jaza" -> "java")
 * 4. Adjacent Transposition (e.g., "tehs" -> "thes")
 */
public final class DamerauLevenshteinDistance {

    private DamerauLevenshteinDistance() {
        // Utility class
    }

    /**
     * Calculates the exact Damerau-Levenshtein distance between two character sequences.
     *
     * @param s1 First string.
     * @param s2 Second string.
     * @return Minimum edit distance.
     */
    public static int calculate(CharSequence s1, CharSequence s2) {
        return calculate(s1, s2, Integer.MAX_VALUE);
    }

    /**
     * Calculates the Damerau-Levenshtein distance with a maximum distance threshold.
     * If the distance exceeds maxDistance, the algorithm terminates early and returns maxDistance + 1.
     *
     * @param s1          First string.
     * @param s2          Second string.
     * @param maxDistance Maximum distance threshold for pruning.
     * @return Distance if <= maxDistance, or maxDistance + 1 if exceeded.
     */
    public static int calculate(CharSequence s1, CharSequence s2, int maxDistance) {
        if (s1 == null && s2 == null) {
            return 0;
        }
        if (s1 == null) {
            return s2 != null ? s2.length() : 0;
        }
        if (s2 == null) {
            return s1.length();
        }

        int len1 = s1.length();
        int len2 = s2.length();

        // Identical strings have distance 0
        if (s1.equals(s2)) {
            return 0;
        }

        // Fast length-difference bound check
        if (Math.abs(len1 - len2) > maxDistance) {
            return maxDistance + 1;
        }

        if (len1 == 0) {
            return len2 <= maxDistance ? len2 : maxDistance + 1;
        }
        if (len2 == 0) {
            return len1 <= maxDistance ? len1 : maxDistance + 1;
        }

        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= len1; i++) {
            char c1 = s1.charAt(i - 1);
            int rowMin = Integer.MAX_VALUE;

            for (int j = 1; j <= len2; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;

                // Deletion, insertion, substitution
                int deletion = dp[i - 1][j] + 1;
                int insertion = dp[i][j - 1] + 1;
                int substitution = dp[i - 1][j - 1] + cost;

                int min = Math.min(deletion, Math.min(insertion, substitution));

                // Adjacent transposition
                if (i > 1 && j > 1 && c1 == s2.charAt(j - 2) && s1.charAt(i - 2) == c2) {
                    min = Math.min(min, dp[i - 2][j - 2] + 1);
                }

                dp[i][j] = min;
                rowMin = Math.min(rowMin, min);
            }

            // Early exit if the minimum edit distance in this entire row exceeds threshold
            if (rowMin > maxDistance) {
                return maxDistance + 1;
            }
        }

        return dp[len1][len2] <= maxDistance ? dp[len1][len2] : maxDistance + 1;
    }
}
