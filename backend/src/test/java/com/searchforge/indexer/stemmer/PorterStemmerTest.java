package com.searchforge.indexer.stemmer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PorterStemmerTest {

    private PorterStemmer stemmer;

    @BeforeEach
    void setUp() {
        stemmer = new PorterStemmer();
    }

    @Test
    @DisplayName("Should reduce crawling inflectional verb variations to common base stem 'crawl'")
    void shouldStemCrawlingVariations() {
        assertEquals("crawl", stemmer.stem("crawl"));
        assertEquals("crawl", stemmer.stem("crawls"));
        assertEquals("crawl", stemmer.stem("crawling"));
        assertEquals("crawl", stemmer.stem("crawled"));
        assertEquals("crawler", stemmer.stem("crawler")); // er stripped in step 4 if m > 1: "crawler" m=2 -> "crawl"
    }

    @Test
    @DisplayName("Should reduce connection and relationship derivational suffixes")
    void shouldStemDerivationalSuffixes() {
        assertEquals("connect", stemmer.stem("connect"));
        assertEquals("connect", stemmer.stem("connecting"));
        assertEquals("connect", stemmer.stem("connection"));
        assertEquals("connect", stemmer.stem("connections"));
        assertEquals("connect", stemmer.stem("connected"));
    }

    @Test
    @DisplayName("Should reduce computing variations to 'comput'")
    void shouldStemComputingVariations() {
        assertEquals("comput", stemmer.stem("compute"));
        assertEquals("comput", stemmer.stem("computer"));
        assertEquals("comput", stemmer.stem("computers"));
        assertEquals("comput", stemmer.stem("computing"));
        assertEquals("comput", stemmer.stem("computation"));
    }

    @Test
    @DisplayName("Should handle Step 1c (y to i) and compound suffixes")
    void shouldHandleStep1cAndCompoundSuffixes() {
        assertEquals("happi", stemmer.stem("happy"));
        assertEquals("electr", stemmer.stem("electrical"));
        assertEquals("relat", stemmer.stem("relational"));
    }

    @Test
    @DisplayName("Should preserve short words and handle null/empty edge cases")
    void shouldHandleShortAndEdgeCases() {
        assertEquals("", stemmer.stem(null));
        assertEquals("", stemmer.stem(""));
        assertEquals("a", stemmer.stem("a"));
        assertEquals("at", stemmer.stem("at"));
        assertEquals("go", stemmer.stem("go"));
        assertEquals("sky", stemmer.stem("sky"));
    }
}
