package com.searchforge.search.hybrid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SearchMode Enum Unit Tests")
class SearchModeTest {

    @Test
    @DisplayName("Null or blank string resolves to LEXICAL mode")
    void fromString_nullOrBlank_shouldReturnLexical() {
        assertEquals(SearchMode.LEXICAL, SearchMode.fromString(null));
        assertEquals(SearchMode.LEXICAL, SearchMode.fromString(""));
        assertEquals(SearchMode.LEXICAL, SearchMode.fromString("   "));
    }

    @ParameterizedTest(name = "Resolves \"{0}\" to LEXICAL")
    @ValueSource(strings = {"lexical", "LEXICAL", "Lexical", "ranked", "bm25", "  lexical  "})
    void fromString_lexicalVariants_shouldReturnLexical(String input) {
        assertEquals(SearchMode.LEXICAL, SearchMode.fromString(input));
    }

    @ParameterizedTest(name = "Resolves \"{0}\" to SEMANTIC")
    @ValueSource(strings = {"semantic", "SEMANTIC", "Semantic", "dense", "vector", "  semantic  "})
    void fromString_semanticVariants_shouldReturnSemantic(String input) {
        assertEquals(SearchMode.SEMANTIC, SearchMode.fromString(input));
    }

    @ParameterizedTest(name = "Resolves \"{0}\" to HYBRID")
    @ValueSource(strings = {"hybrid", "HYBRID", "Hybrid", "rrf", "combined", "  hybrid  "})
    void fromString_hybridVariants_shouldReturnHybrid(String input) {
        assertEquals(SearchMode.HYBRID, SearchMode.fromString(input));
    }

    @ParameterizedTest(name = "Rejects unknown mode \"{0}\" with descriptive message")
    @ValueSource(strings = {"quantum", "fuzzy", "exact", "lucene", "invalid"})
    void fromString_invalidMode_shouldThrowIllegalArgumentException(String input) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SearchMode.fromString(input));
        assertTrue(ex.getMessage().contains("Invalid search mode: '" + input + "'"));
        assertTrue(ex.getMessage().contains("Supported modes are: lexical, semantic, hybrid"));
    }
}
