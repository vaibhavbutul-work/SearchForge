package com.searchforge.query.autocomplete.service;

import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.TermMetadata;
import com.searchforge.query.autocomplete.model.AutocompleteSuggestion;
import com.searchforge.query.autocomplete.trie.AutocompleteTrie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production implementation of AutocompleteService.
 *
 * Uses an AtomicReference to an in-memory AutocompleteTrie to allow lock-free
 * reads and atomic index synchronization without search pauses.
 */
@Service
public class DefaultAutocompleteService implements AutocompleteService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAutocompleteService.class);

    private final InvertedIndex invertedIndex;
    private final AtomicReference<AutocompleteTrie> trieRef;

    public DefaultAutocompleteService(InvertedIndex invertedIndex) {
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "InvertedIndex cannot be null");
        this.trieRef = new AtomicReference<>(new AutocompleteTrie());
    }

    @Override
    public List<AutocompleteSuggestion> suggest(String prefix, int limit) {
        if (prefix == null || prefix.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }

        // Auto-warm Trie on first query if index has terms but Trie is uninitialized
        if (trieRef.get().size() == 0 && invertedIndex.getVocabularySize() > 0) {
            syncWithIndex();
        }

        return trieRef.get().searchPrefix(prefix, limit);
    }

    @Override
    public void syncWithIndex() {
        log.info("Synchronizing autocomplete Trie with inverted index vocabulary...");
        AutocompleteTrie newTrie = new AutocompleteTrie();

        Map<String, TermMetadata> dictionary = invertedIndex.getDictionaryMetadata();
        if (dictionary != null && !dictionary.isEmpty()) {
            for (TermMetadata metadata : dictionary.values()) {
                newTrie.insert(metadata.term(), metadata.totalTermFrequency(), metadata.documentFrequency());
            }
        }

        trieRef.set(newTrie);
        log.info("Autocomplete Trie synchronization complete. Total terms loaded: {}", newTrie.size());
    }

    @Override
    public int getVocabularySize() {
        return trieRef.get().size();
    }
}
