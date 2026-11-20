package com.searchforge.query.synonym.dictionary;

import com.searchforge.query.synonym.config.SynonymExpansionMode;
import com.searchforge.query.synonym.config.SynonymProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySynonymDictionaryTest {

    private InMemorySynonymDictionary dictionary;

    @BeforeEach
    void setUp() {
        dictionary = new InMemorySynonymDictionary();
        dictionary.clear();
    }

    @Test
    @DisplayName("Should correctly register and retrieve one-way mappings")
    void shouldHandleOneWayMappings() {
        dictionary.addOneWay("k8s", "kubernetes");

        Set<String> k8sSynonyms = dictionary.getSynonyms("k8s", SynonymExpansionMode.ONE_WAY);
        assertThat(k8sSynonyms).containsExactly("kubernetes");

        // Verify one-way: "kubernetes" should NOT expand back to "k8s"
        Set<String> reverse = dictionary.getSynonyms("kubernetes", SynonymExpansionMode.ONE_WAY);
        assertThat(reverse).isEmpty();
    }

    @Test
    @DisplayName("Should correctly register and retrieve bidirectional mappings in both directions")
    void shouldHandleBidirectionalMappings() {
        dictionary.addBidirectional("db", "database");

        Set<String> fromDb = dictionary.getSynonyms("db", SynonymExpansionMode.BIDIRECTIONAL);
        assertThat(fromDb).containsExactly("database");

        Set<String> fromDatabase = dictionary.getSynonyms("database", SynonymExpansionMode.BIDIRECTIONAL);
        assertThat(fromDatabase).containsExactly("db");
    }

    @Test
    @DisplayName("Should support multiple synonyms per term")
    void shouldSupportMultipleSynonyms() {
        dictionary.addBidirectional("db", List.of("database", "rdbms", "sql"));

        Set<String> synonyms = dictionary.getSynonyms("db", SynonymExpansionMode.BIDIRECTIONAL);
        assertThat(synonyms).containsExactlyInAnyOrder("database", "rdbms", "sql");
    }

    @Test
    @DisplayName("Should prevent self-mapping (term expanding to itself)")
    void shouldPreventSelfMapping() {
        dictionary.addOneWay("database", "database");
        dictionary.addBidirectional("database", "database");

        assertThat(dictionary.getSynonyms("database", SynonymExpansionMode.BIDIRECTIONAL)).isEmpty();
    }

    @Test
    @DisplayName("Should be completely case-insensitive")
    void shouldBeCaseInsensitive() {
        dictionary.addBidirectional("PostgreSQL", "Postgres");

        assertThat(dictionary.getSynonyms("postgresql", SynonymExpansionMode.BIDIRECTIONAL)).contains("postgres");
        assertThat(dictionary.getSynonyms("POSTGRESQL", SynonymExpansionMode.BIDIRECTIONAL)).contains("postgres");
        assertThat(dictionary.getSynonyms("PostGres", SynonymExpansionMode.BIDIRECTIONAL)).contains("postgresql");
    }

    @Test
    @DisplayName("Should return empty set when expansion mode is DISABLED")
    void shouldRespectDisabledMode() {
        dictionary.addBidirectional("db", "database");

        Set<String> synonyms = dictionary.getSynonyms("db", SynonymExpansionMode.DISABLED);
        assertThat(synonyms).isEmpty();
        assertThat(dictionary.hasSynonyms("db", SynonymExpansionMode.DISABLED)).isFalse();
    }

    @Test
    @DisplayName("Should ignore bidirectional mappings when mode is ONE_WAY")
    void shouldIgnoreBidirectionalInOneWayMode() {
        dictionary.addOneWay("k8s", "kubernetes");
        dictionary.addBidirectional("db", "database");

        assertThat(dictionary.getSynonyms("k8s", SynonymExpansionMode.ONE_WAY)).contains("kubernetes");
        assertThat(dictionary.getSynonyms("db", SynonymExpansionMode.ONE_WAY)).isEmpty();
    }
}
