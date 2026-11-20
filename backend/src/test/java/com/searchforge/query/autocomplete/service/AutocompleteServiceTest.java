package com.searchforge.query.autocomplete.service;

import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.TermMetadata;
import com.searchforge.query.autocomplete.model.AutocompleteSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutocompleteServiceTest {

    @Mock
    private InvertedIndex invertedIndex;

    private DefaultAutocompleteService autocompleteService;

    @BeforeEach
    void setUp() {
        autocompleteService = new DefaultAutocompleteService(invertedIndex);
    }

    @Test
    @DisplayName("Should sync Trie from inverted index vocabulary")
    void shouldSyncWithIndex() {
        Map<String, TermMetadata> mockDict = Map.of(
                "spring", new TermMetadata("spring", 20, 100),
                "springboot", new TermMetadata("springboot", 15, 80),
                "security", new TermMetadata("security", 10, 40)
        );

        given(invertedIndex.getDictionaryMetadata()).willReturn(mockDict);

        autocompleteService.syncWithIndex();

        assertThat(autocompleteService.getVocabularySize()).isEqualTo(3);

        List<AutocompleteSuggestion> suggestions = autocompleteService.suggest("spring", 5);
        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.get(0).term()).isEqualTo("spring");
        assertThat(suggestions.get(1).term()).isEqualTo("springboot");
    }

    @Test
    @DisplayName("Should auto-sync on first query if index has terms but Trie is uninitialized")
    void shouldAutoWarmOnFirstQuery() {
        Map<String, TermMetadata> mockDict = Map.of(
                "database", new TermMetadata("database", 5, 25)
        );

        given(invertedIndex.getVocabularySize()).willReturn(1);
        given(invertedIndex.getDictionaryMetadata()).willReturn(mockDict);

        List<AutocompleteSuggestion> suggestions = autocompleteService.suggest("data", 5);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).term()).isEqualTo("database");
        verify(invertedIndex, times(1)).getDictionaryMetadata();
    }

    @Test
    @DisplayName("Should return empty list for blank or invalid prefix")
    void shouldReturnEmptyForBlankPrefix() {
        assertThat(autocompleteService.suggest(null, 5)).isEmpty();
        assertThat(autocompleteService.suggest("", 5)).isEmpty();
        assertThat(autocompleteService.suggest("   ", 5)).isEmpty();
        assertThat(autocompleteService.suggest("data", 0)).isEmpty();
        assertThat(autocompleteService.suggest("data", -1)).isEmpty();
    }
}
