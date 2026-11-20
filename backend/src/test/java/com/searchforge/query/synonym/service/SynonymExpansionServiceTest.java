package com.searchforge.query.synonym.service;

import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.query.synonym.config.SynonymExpansionMode;
import com.searchforge.query.synonym.config.SynonymProperties;
import com.searchforge.query.synonym.dictionary.InMemorySynonymDictionary;
import com.searchforge.query.synonym.model.ExpandedQuery;
import com.searchforge.query.synonym.model.ExpandedTerm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SynonymExpansionServiceTest {

    private InMemorySynonymDictionary dictionary;
    private SynonymProperties properties;
    private DefaultSynonymExpansionService expansionService;

    @BeforeEach
    void setUp() {
        properties = new SynonymProperties();
        properties.setMode(SynonymExpansionMode.BIDIRECTIONAL);
        properties.setDiscount(0.5);
        properties.setMaxSynonymsPerTerm(3);
        properties.setOneWay(null);
        properties.setBidirectional(null);

        dictionary = new InMemorySynonymDictionary(properties);
        expansionService = new DefaultSynonymExpansionService(dictionary, properties, new StopWordFilter());
    }

    @Test
    @DisplayName("Should expand query terms with priority weight for originals and discount for synonyms")
    void shouldExpandWithWeights() {
        dictionary.addBidirectional("db", "database");

        ExpandedQuery expandedQuery = expansionService.expandQuery("db");

        assertThat(expandedQuery.isExpanded()).isTrue();
        assertThat(expandedQuery.originalQuery()).isEqualTo("db");
        assertThat(expandedQuery.terms()).hasSize(2);

        ExpandedTerm original = expandedQuery.terms().get(0);
        assertThat(original.term()).isEqualTo("db");
        assertThat(original.weight()).isEqualTo(1.0);
        assertThat(original.isOriginal()).isTrue();

        ExpandedTerm synonym = expandedQuery.terms().get(1);
        assertThat(synonym.term()).isEqualTo("database");
        assertThat(synonym.weight()).isEqualTo(0.5);
        assertThat(synonym.isOriginal()).isFalse();
    }

    @Test
    @DisplayName("Should return unexpanded query when mode is DISABLED")
    void shouldRespectDisabledMode() {
        dictionary.addBidirectional("db", "database");
        properties.setMode(SynonymExpansionMode.DISABLED);

        ExpandedQuery expandedQuery = expansionService.expandQuery("db");

        assertThat(expandedQuery.isExpanded()).isFalse();
        assertThat(expandedQuery.terms()).hasSize(1);
        assertThat(expandedQuery.terms().get(0).term()).isEqualTo("db");
        assertThat(expandedQuery.terms().get(0).weight()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should prevent circular expansion loops and duplicate terms")
    void shouldPreventCircularExpansion() {
        // Query has both "db" and "database"
        // "db" expands to "database", and "database" expands to "db"
        dictionary.addBidirectional("db", "database");

        ExpandedQuery expandedQuery = expansionService.expandQuery("db database");

        // Must not duplicate "database" or "db"
        assertThat(expandedQuery.terms()).hasSize(2);
        List<String> terms = expandedQuery.terms().stream().map(ExpandedTerm::term).toList();
        assertThat(terms).containsExactly("db", "database");
        assertThat(expandedQuery.terms().get(0).weight()).isEqualTo(1.0);
        assertThat(expandedQuery.terms().get(1).weight()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should strictly enforce maxSynonymsPerTerm limit")
    void shouldEnforceMaxSynonymsLimit() {
        properties.setMaxSynonymsPerTerm(2);
        dictionary.addBidirectional("db", List.of("database", "rdbms", "sql", "storage"));

        ExpandedQuery expandedQuery = expansionService.expandQuery("db");

        // 1 original + max 2 synonyms = 3 terms
        assertThat(expandedQuery.terms()).hasSize(3);
        assertThat(expandedQuery.terms().get(0).term()).isEqualTo("db");
        assertThat(expandedQuery.terms().get(0).isOriginal()).isTrue();
        assertThat(expandedQuery.terms().get(1).isOriginal()).isFalse();
        assertThat(expandedQuery.terms().get(2).isOriginal()).isFalse();
    }

    @Test
    @DisplayName("Should filter out stop words from being added as synonyms")
    void shouldFilterStopWordSynonyms() {
        // "the" and "on" are English stop words
        dictionary.addOneWay("sample", List.of("example", "the", "on"));

        ExpandedQuery expandedQuery = expansionService.expandQuery("sample");

        List<String> termStrings = expandedQuery.terms().stream().map(ExpandedTerm::term).toList();
        assertThat(termStrings).contains("sample", "example");
        assertThat(termStrings).doesNotContain("the", "on");
    }

    @Test
    @DisplayName("Should handle empty and blank queries gracefully")
    void shouldHandleBlankQueries() {
        ExpandedQuery nullQuery = expansionService.expandQuery(null);
        assertThat(nullQuery.isExpanded()).isFalse();
        assertThat(nullQuery.terms()).isEmpty();

        ExpandedQuery blankQuery = expansionService.expandQuery("   ");
        assertThat(blankQuery.isExpanded()).isFalse();
        assertThat(blankQuery.terms()).isEmpty();
    }
}
