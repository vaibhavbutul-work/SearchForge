package com.searchforge.query.spelling.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DamerauLevenshteinDistanceTest {

    @Test
    @DisplayName("Identical strings should have edit distance 0")
    void shouldReturnZeroForIdenticalStrings() {
        assertThat(DamerauLevenshteinDistance.calculate("", "")).isEqualTo(0);
        assertThat(DamerauLevenshteinDistance.calculate("java", "java")).isEqualTo(0);
        assertThat(DamerauLevenshteinDistance.calculate("postgresql", "postgresql")).isEqualTo(0);
    }

    @Test
    @DisplayName("Single insertion should have edit distance 1")
    void shouldDetectSingleInsertion() {
        assertThat(DamerauLevenshteinDistance.calculate("btre", "btree")).isEqualTo(1);
        assertThat(DamerauLevenshteinDistance.calculate("jav", "java")).isEqualTo(1);
        assertThat(DamerauLevenshteinDistance.calculate("index", "indexes")).isEqualTo(2);
    }

    @Test
    @DisplayName("Single deletion should have edit distance 1")
    void shouldDetectSingleDeletion() {
        assertThat(DamerauLevenshteinDistance.calculate("javva", "java")).isEqualTo(1);
        assertThat(DamerauLevenshteinDistance.calculate("postingss", "postings")).isEqualTo(1);
    }

    @Test
    @DisplayName("Single substitution should have edit distance 1")
    void shouldDetectSingleSubstitution() {
        assertThat(DamerauLevenshteinDistance.calculate("postgreql", "postgresql")).isEqualTo(1);
        assertThat(DamerauLevenshteinDistance.calculate("jaza", "java")).isEqualTo(1);
    }

    @Test
    @DisplayName("Adjacent transposition should have edit distance 1")
    void shouldDetectAdjacentTransposition() {
        assertThat(DamerauLevenshteinDistance.calculate("tehs", "thes")).isEqualTo(1);
        assertThat(DamerauLevenshteinDistance.calculate("sprnig", "spring")).isEqualTo(1);
        assertThat(DamerauLevenshteinDistance.calculate("btree", "btere")).isEqualTo(1);
    }

    @Test
    @DisplayName("Non-adjacent transposition should count as two substitutions")
    void shouldHandleNonAdjacentTransposition() {
        // 'a' and 'c' swapped in "acb" vs "bca"
        assertThat(DamerauLevenshteinDistance.calculate("ab", "ba")).isEqualTo(1); // adjacent
        assertThat(DamerauLevenshteinDistance.calculate("abcd", "adcb")).isEqualTo(2); // non-adjacent
    }

    @Test
    @DisplayName("Should respect maxDistance threshold pruning")
    void shouldRespectMaxDistance() {
        // "abcdef" vs "xyz" distance is 6. With maxDistance 2, should return 3 (maxDistance + 1)
        assertThat(DamerauLevenshteinDistance.calculate("abcdef", "xyz", 2)).isEqualTo(3);

        // "postgreql" vs "postgresql" distance is 1 <= 2 => returns 1
        assertThat(DamerauLevenshteinDistance.calculate("postgreql", "postgresql", 2)).isEqualTo(1);

        // Length difference > maxDistance => immediate early exit
        assertThat(DamerauLevenshteinDistance.calculate("a", "aaaa", 2)).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({
            "kitten, sitting, 3",
            "saturday, sunday, 3",
            "rosettacode, raisethysword, 8"
    })
    @DisplayName("Should match standard literature Levenshtein benchmarks")
    void shouldMatchStandardBenchmarks(String s1, String s2, int expected) {
        assertThat(DamerauLevenshteinDistance.calculate(s1, s2)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should handle null and empty string boundaries")
    void shouldHandleNullAndEmpty() {
        assertThat(DamerauLevenshteinDistance.calculate(null, null)).isEqualTo(0);
        assertThat(DamerauLevenshteinDistance.calculate(null, "java")).isEqualTo(4);
        assertThat(DamerauLevenshteinDistance.calculate("java", null)).isEqualTo(4);
        assertThat(DamerauLevenshteinDistance.calculate("", "abc")).isEqualTo(3);
        assertThat(DamerauLevenshteinDistance.calculate("abc", "")).isEqualTo(3);
    }
}
