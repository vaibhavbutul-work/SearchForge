package com.searchforge.query.autocomplete.trie;

import com.searchforge.query.autocomplete.model.AutocompleteSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutocompleteTrieTest {

    private AutocompleteTrie trie;

    @BeforeEach
    void setUp() {
        trie = new AutocompleteTrie();
    }

    @Test
    @DisplayName("Should insert terms and report accurate size")
    void shouldInsertAndReportSize() {
        assertThat(trie.size()).isEqualTo(0);

        trie.insert("postgresql", 120, 35);
        trie.insert("postgres", 80, 25);
        trie.insert("postings", 50, 20);

        assertThat(trie.size()).isEqualTo(3);
        assertThat(trie.contains("postgresql")).isTrue();
        assertThat(trie.contains("postgres")).isTrue();
        assertThat(trie.contains("postings")).isTrue();
        assertThat(trie.contains("post")).isFalse();
    }

    @Test
    @DisplayName("Should update statistics without duplicating term on duplicate insertion")
    void shouldHandleDuplicateInsertion() {
        trie.insert("java", 10, 2);
        assertThat(trie.size()).isEqualTo(1);

        // Re-insert with updated frequencies
        trie.insert("java", 100, 20);
        assertThat(trie.size()).isEqualTo(1);

        List<AutocompleteSuggestion> suggestions = trie.searchPrefix("java", 5);
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).ctf()).isEqualTo(100);
        assertThat(suggestions.get(0).df()).isEqualTo(20);
    }

    @Test
    @DisplayName("Should perform case-insensitive insertion and prefix retrieval")
    void shouldBeCaseInsensitive() {
        trie.insert("PostgreSQL", 100, 30);
        trie.insert("POSTINGS", 60, 15);

        List<AutocompleteSuggestion> lower = trie.searchPrefix("post", 10);
        List<AutocompleteSuggestion> upper = trie.searchPrefix("POST", 10);
        List<AutocompleteSuggestion> mixed = trie.searchPrefix("PoSt", 10);

        assertThat(lower).hasSize(2);
        assertThat(upper).hasSize(2);
        assertThat(mixed).hasSize(2);

        assertThat(lower.get(0).term()).isEqualTo("postgresql");
    }

    @Test
    @DisplayName("Should return empty list for missing prefix, empty prefix, or blank prefix")
    void shouldHandleMissingAndBlankPrefixes() {
        trie.insert("search", 50, 10);

        assertThat(trie.searchPrefix(null, 5)).isEmpty();
        assertThat(trie.searchPrefix("", 5)).isEmpty();
        assertThat(trie.searchPrefix("   ", 5)).isEmpty();
        assertThat(trie.searchPrefix("xyz", 5)).isEmpty();
        assertThat(trie.searchPrefix("search", 0)).isEmpty();
        assertThat(trie.searchPrefix("search", -1)).isEmpty();
    }

    @Test
    @DisplayName("Should rank suggestions deterministically by score descending")
    void shouldRankSuggestionsByScoreDescending() {
        // formula: ln(1 + ctf) * sqrt(df)
        // termA: ctf=100, df=36 => ln(101)*6 = 4.6151 * 6 = 27.69
        // termB: ctf=50, df=16 => ln(51)*4 = 3.9318 * 4 = 15.73
        // termC: ctf=20, df=4 => ln(21)*2 = 3.0445 * 2 = 6.09
        trie.insert("termc", 20, 4);
        trie.insert("terma", 100, 36);
        trie.insert("termb", 50, 16);

        List<AutocompleteSuggestion> results = trie.searchPrefix("term", 5);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).term()).isEqualTo("terma");
        assertThat(results.get(1).term()).isEqualTo("termb");
        assertThat(results.get(2).term()).isEqualTo("termc");

        assertThat(results.get(0).score()).isGreaterThan(results.get(1).score());
        assertThat(results.get(1).score()).isGreaterThan(results.get(2).score());
    }

    @Test
    @DisplayName("Should break ties alphabetically when scores are identical")
    void shouldBreakTiesAlphabetically() {
        // Both have exact same CTF and DF => exact same score
        trie.insert("zebra", 50, 10);
        trie.insert("alpha", 50, 10);

        List<AutocompleteSuggestion> results = trie.searchPrefix("", 5);
        assertThat(results).isEmpty();

        List<AutocompleteSuggestion> allA = trie.searchPrefix("a", 5);
        assertThat(allA).hasSize(1);
        assertThat(allA.get(0).term()).isEqualTo("alpha");

        // Insert another with same score
        trie.insert("beta", 50, 10);
        trie.insert("apple", 50, 10);

        List<AutocompleteSuggestion> aPrefix = trie.searchPrefix("a", 5);
        assertThat(aPrefix).hasSize(2);
        // "alpha" vs "apple" have same score => "alpha" comes before "apple"
        assertThat(aPrefix.get(0).term()).isEqualTo("alpha");
        assertThat(aPrefix.get(1).term()).isEqualTo("apple");
    }

    @Test
    @DisplayName("Should strictly enforce Top-K result limits")
    void shouldEnforceTopKLimit() {
        for (int i = 1; i <= 20; i++) {
            trie.insert("testterm" + String.format("%02d", i), i * 10, i * 2);
        }

        assertThat(trie.size()).isEqualTo(20);

        List<AutocompleteSuggestion> top3 = trie.searchPrefix("test", 3);
        assertThat(top3).hasSize(3);
        // The highest frequencies should be first
        assertThat(top3.get(0).term()).isEqualTo("testterm20");
        assertThat(top3.get(1).term()).isEqualTo("testterm19");
        assertThat(top3.get(2).term()).isEqualTo("testterm18");
    }

    @Test
    @DisplayName("Should include prefix when prefix itself is a valid word")
    void shouldIncludePrefixWhenPrefixIsWord() {
        trie.insert("post", 10, 5);
        trie.insert("postgres", 50, 20);
        trie.insert("postgresql", 100, 30);

        List<AutocompleteSuggestion> results = trie.searchPrefix("post", 10);

        assertThat(results).hasSize(3);
        assertThat(results.stream().map(AutocompleteSuggestion::term))
                .containsExactly("postgresql", "postgres", "post");
    }

    @Test
    @DisplayName("Should handle 1,000+ vocabulary terms with sub-millisecond retrieval")
    void shouldPerformAtScale() {
        for (int i = 0; i < 1500; i++) {
            trie.insert("index_term_" + i, i + 1, (i % 50) + 1);
        }

        assertThat(trie.size()).isEqualTo(1500);

        long start = System.nanoTime();
        List<AutocompleteSuggestion> results = trie.searchPrefix("index_term_1", 10);
        long elapsedNanos = System.nanoTime() - start;

        assertThat(results).hasSize(10);
        // Retrieval should be extremely fast (under 2ms)
        assertThat(elapsedNanos).isLessThan(5_000_000);
    }
}
