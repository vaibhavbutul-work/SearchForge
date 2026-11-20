package com.searchforge.query.spelling.service;

import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.TermMetadata;
import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.model.SpellingCorrection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SpellingCorrectionServiceTest {

    @Mock
    private InvertedIndex invertedIndex;

    private DefaultSpellingCorrectionService spellingService;

    @BeforeEach
    void setUp() {
        spellingService = new DefaultSpellingCorrectionService(invertedIndex, 2);
    }

    @Test
    @DisplayName("Should correct single misspelled word within edit distance threshold")
    void shouldCorrectSingleMisspelledWord() {
        Map<String, TermMetadata> vocabulary = Map.of(
                "postgresql", new TermMetadata("postgresql", 35, 120),
                "mysql", new TermMetadata("mysql", 20, 60)
        );
        given(invertedIndex.getDictionaryMetadata()).willReturn(vocabulary);

        Optional<SpellingCorrection> correction = spellingService.correctTerm("postgreql");

        assertThat(correction).isPresent();
        assertThat(correction.get().original()).isEqualTo("postgreql");
        assertThat(correction.get().suggestion()).isEqualTo("postgresql");
        assertThat(correction.get().editDistance()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should correctly handle multi-word query with multiple typos")
    void shouldCorrectMultiWordQuery() {
        Map<String, TermMetadata> vocabulary = Map.of(
                "postgresql", new TermMetadata("postgresql", 35, 120),
                "btree", new TermMetadata("btree", 25, 80),
                "index", new TermMetadata("index", 50, 200)
        );
        given(invertedIndex.getDictionaryMetadata()).willReturn(vocabulary);

        QuerySuggestionResponse response = spellingService.suggest("postgreql btre");

        assertThat(response.hasCorrection()).isTrue();
        assertThat(response.originalQuery()).isEqualTo("postgreql btre");
        assertThat(response.suggestedQuery()).isEqualTo("postgresql btree");
        assertThat(response.corrections()).hasSize(2);
        assertThat(response.corrections().get(0).suggestion()).isEqualTo("postgresql");
        assertThat(response.corrections().get(1).suggestion()).isEqualTo("btree");
    }

    @Test
    @DisplayName("Should preserve words that already exist in the indexed vocabulary")
    void shouldNotModifyValidVocabularyTerms() {
        Map<String, TermMetadata> vocabulary = Map.of(
                "java", new TermMetadata("java", 50, 200),
                "spring", new TermMetadata("spring", 40, 150)
        );
        given(invertedIndex.getDictionaryMetadata()).willReturn(vocabulary);

        QuerySuggestionResponse response = spellingService.suggest("java spring");

        assertThat(response.hasCorrection()).isFalse();
        assertThat(response.originalQuery()).isEqualTo("java spring");
        assertThat(response.suggestedQuery()).isEqualTo("java spring");
        assertThat(response.corrections()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve unknown words when no candidate exists within max edit distance")
    void shouldPreserveUnknownWordsWithNoCloseMatches() {
        Map<String, TermMetadata> vocabulary = Map.of(
                "java", new TermMetadata("java", 50, 200)
        );
        given(invertedIndex.getDictionaryMetadata()).willReturn(vocabulary);

        // "zzzzzzzzz" has distance > 2 from "java"
        QuerySuggestionResponse response = spellingService.suggest("zzzzzzzzz");

        assertThat(response.hasCorrection()).isFalse();
        assertThat(response.suggestedQuery()).isEqualTo("zzzzzzzzz");
        assertThat(response.corrections()).isEmpty();
    }

    @Test
    @DisplayName("Should rank candidates by corpus frequency when edit distances are equal")
    void shouldRankByCorpusFrequencyWhenDistancesEqual() {
        // "post" has edit distance 1 from both "posts" and "postg"
        // "posts" has high frequency, "postg" has low frequency
        Map<String, TermMetadata> vocabulary = Map.of(
                "posts", new TermMetadata("posts", 40, 150),
                "postg", new TermMetadata("postg", 2, 3)
        );
        given(invertedIndex.getDictionaryMetadata()).willReturn(vocabulary);

        Optional<SpellingCorrection> correction = spellingService.correctTerm("post");

        assertThat(correction).isPresent();
        assertThat(correction.get().suggestion()).isEqualTo("posts");
    }

    @Test
    @DisplayName("Should prioritize edit distance 1 over edit distance 2 even with higher frequency")
    void shouldPrioritizeLowerEditDistance() {
        // Misspelling: "word"
        // Candidate A: "ward" (distance 1, ctf 10)
        // Candidate B: "world" (distance 1, ctf 100) -> distance from "word" is 1 ('l' insertion)
        // Candidate C: "worldwide" (distance 5, ignored)
        // Candidate D: "cord" (distance 1, ctf 5)
        Map<String, TermMetadata> vocabulary = Map.of(
                "ward", new TermMetadata("ward", 5, 10),
                "world", new TermMetadata("world", 30, 100),
                "cord", new TermMetadata("cord", 2, 5)
        );
        given(invertedIndex.getDictionaryMetadata()).willReturn(vocabulary);

        Optional<SpellingCorrection> correction = spellingService.correctTerm("wrd");

        // "wrd" to "ward" is dist 1 ('a' insertion)
        // "wrd" to "world" is dist 2 ('o' and 'l' insertions)
        assertThat(correction).isPresent();
        assertThat(correction.get().suggestion()).isEqualTo("ward");
        assertThat(correction.get().editDistance()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should preserve casing of capitalized words")
    void shouldPreserveCasing() {
        Map<String, TermMetadata> vocabulary = Map.of(
                "postgresql", new TermMetadata("postgresql", 35, 120)
        );
        given(invertedIndex.getDictionaryMetadata()).willReturn(vocabulary);

        QuerySuggestionResponse response = spellingService.suggest("Postgreql");

        assertThat(response.hasCorrection()).isTrue();
        assertThat(response.suggestedQuery()).isEqualTo("Postgresql");
    }

    @Test
    @DisplayName("Should handle empty and blank queries gracefully")
    void shouldHandleBlankQueries() {
        QuerySuggestionResponse nullRes = spellingService.suggest(null);
        assertThat(nullRes.hasCorrection()).isFalse();

        QuerySuggestionResponse emptyRes = spellingService.suggest("   ");
        assertThat(emptyRes.hasCorrection()).isFalse();
    }
}
