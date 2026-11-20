package com.searchforge.semantic.tokenizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WordPieceTokenizer Unit Tests")
class WordPieceTokenizerTest {

    private WordPieceTokenizer tokenizer;

    @BeforeEach
    void setUp() {
        // Build an in-memory controlled vocabulary covering all test cases
        Map<String, Integer> vocab = Map.ofEntries(
                Map.entry("[PAD]", 0),
                Map.entry("[UNK]", 100),
                Map.entry("[CLS]", 101),
                Map.entry("[SEP]", 102),
                Map.entry("[MASK]", 103),
                Map.entry("java", 1000),
                Map.entry("spring", 1001),
                Map.entry("boot", 1002),
                Map.entry("search", 1003),
                Map.entry("engine", 1004),
                Map.entry("micro", 1005),
                Map.entry("##service", 1006),
                Map.entry("##services", 1007),
                Map.entry("un", 1008),
                Map.entry("##break", 1009),
                Map.entry("##able", 1010),
                Map.entry("quick", 1011),
                Map.entry("brown", 1012),
                Map.entry("fox", 1013),
                Map.entry("fast", 1014),
                Map.entry(",", 1015),
                Map.entry("!", 1016),
                Map.entry("?", 1017),
                Map.entry(".", 1018),
                Map.entry("-", 1019)
        );
        tokenizer = new WordPieceTokenizer(vocab);
    }

    @Test
    @DisplayName("Tokenize common English words with correct token IDs and special tokens")
    void tokenize_commonEnglishWords() {
        TokenizationResult result = tokenizer.tokenize("java spring boot");

        assertEquals(List.of("[CLS]", "java", "spring", "boot", "[SEP]"), result.tokens());
        assertArrayEquals(new long[]{101L, 1000L, 1001L, 1002L, 102L}, result.tokenIds());
        assertArrayEquals(new long[]{1L, 1L, 1L, 1L, 1L}, result.attentionMask());
        assertArrayEquals(new long[]{0L, 0L, 0L, 0L, 0L}, result.tokenTypeIds());
        assertEquals(5, result.sequenceLength());
        assertFalse(result.truncated());
    }

    @Test
    @DisplayName("Case-insensitive normalization converts uppercase to lowercase vocabulary IDs")
    void tokenize_caseInsensitivity() {
        TokenizationResult result1 = tokenizer.tokenize("JAVA SPRING");
        TokenizationResult result2 = tokenizer.tokenize("java spring");

        assertEquals(result1.tokens(), result2.tokens());
        assertArrayEquals(result1.tokenIds(), result2.tokenIds());
    }

    @Test
    @DisplayName("Punctuation is isolated as distinct tokens")
    void tokenize_punctuationSplitting() {
        TokenizationResult result = tokenizer.tokenize("search, engine!");

        assertEquals(List.of("[CLS]", "search", ",", "engine", "!", "[SEP]"), result.tokens());
        assertArrayEquals(new long[]{101L, 1003L, 1015L, 1004L, 1016L, 102L}, result.tokenIds());
    }

    @Test
    @DisplayName("Multiple whitespaces, tabs, and newlines are normalized cleanly")
    void tokenize_whitespaceHandling() {
        TokenizationResult result = tokenizer.tokenize("   java \t\t  search \n\n engine   ");

        assertEquals(List.of("[CLS]", "java", "search", "engine", "[SEP]"), result.tokens());
        assertEquals(5, result.sequenceLength());
    }

    @Test
    @DisplayName("Subword segmentation splits unknown compound words using '##' prefixes")
    void tokenize_subwordSegmentation() {
        TokenizationResult result = tokenizer.tokenize("microservices unbreakable");

        assertEquals(List.of("[CLS]", "micro", "##services", "un", "##break", "##able", "[SEP]"), result.tokens());
        assertArrayEquals(new long[]{101L, 1005L, 1007L, 1008L, 1009L, 1010L, 102L}, result.tokenIds());
    }

    @Test
    @DisplayName("Unknown words not decomposable in vocabulary emit [UNK] token")
    void tokenize_unknownWords_emitUnk() {
        TokenizationResult result = tokenizer.tokenize("java xylophone boot");

        assertEquals(List.of("[CLS]", "java", "[UNK]", "boot", "[SEP]"), result.tokens());
        assertArrayEquals(new long[]{101L, 1000L, 100L, 1002L, 102L}, result.tokenIds());
    }

    @Test
    @DisplayName("Empty or blank input emits minimal valid [CLS] and [SEP] sequence")
    void tokenize_emptyOrBlankInput() {
        TokenizationResult r1 = tokenizer.tokenize("");
        assertEquals(List.of("[CLS]", "[SEP]"), r1.tokens());
        assertArrayEquals(new long[]{101L, 102L}, r1.tokenIds());
        assertFalse(r1.truncated());

        TokenizationResult r2 = tokenizer.tokenize("    ");
        assertEquals(List.of("[CLS]", "[SEP]"), r2.tokens());
        assertArrayEquals(new long[]{101L, 102L}, r2.tokenIds());

        TokenizationResult r3 = tokenizer.tokenize(null);
        assertEquals(List.of("[CLS]", "[SEP]"), r3.tokens());
        assertArrayEquals(new long[]{101L, 102L}, r3.tokenIds());
    }

    @Test
    @DisplayName("Truncation enforces maxSequenceLength and sets truncated flag")
    void tokenize_truncationEnforced() {
        // 5 words -> [CLS] + 5 tokens + [SEP] = 7 tokens total. With max=5, truncates to 3 words + 2 special = 5 tokens.
        TokenizationResult result = tokenizer.tokenize("java spring boot search engine", 5);

        assertTrue(result.truncated());
        assertEquals(5, result.sequenceLength());
        assertEquals(List.of("[CLS]", "java", "spring", "boot", "[SEP]"), result.tokens());
        assertArrayEquals(new long[]{101L, 1000L, 1001L, 1002L, 102L}, result.tokenIds());
    }

    @Test
    @DisplayName("Loads real classpath vocab.txt containing 30,522 MiniLM/BERT tokens")
    void tokenize_loadClasspathVocab() {
        WordPieceTokenizer realTokenizer = new WordPieceTokenizer("classpath:models/vocab.txt");

        assertEquals(30522, realTokenizer.getVocabSize());
        assertEquals(101, realTokenizer.getClsTokenId());
        assertEquals(102, realTokenizer.getSepTokenId());
        assertEquals(0, realTokenizer.getPadTokenId());
        assertEquals(100, realTokenizer.getUnkTokenId());

        TokenizationResult res = realTokenizer.tokenize("Java virtual threads and microservices");
        assertTrue(res.tokens().contains("java"));
        assertTrue(res.tokens().contains("virtual"));
        assertTrue(res.tokens().contains("threads"));
        assertTrue(res.tokens().contains("micro"));
        assertEquals(101L, res.tokenIds()[0]);
        assertEquals(102L, res.tokenIds()[res.tokenIds().length - 1]);
    }

    @Test
    @DisplayName("Invalid vocabulary path throws TokenizerException")
    void tokenize_invalidVocabPath_throwsException() {
        assertThrows(TokenizerException.class, () -> new WordPieceTokenizer("non_existent_vocab_file.txt"));
    }

    @Test
    @DisplayName("Token and ID bidirectional conversion")
    void tokenAndIdConversion() {
        assertEquals(1000L, tokenizer.convertTokenToId("java"));
        assertEquals("java", tokenizer.convertIdToToken(1000L));
        assertEquals(100L, tokenizer.convertTokenToId("nonexistent"));
        assertNull(tokenizer.convertIdToToken(999999L));
    }
}
