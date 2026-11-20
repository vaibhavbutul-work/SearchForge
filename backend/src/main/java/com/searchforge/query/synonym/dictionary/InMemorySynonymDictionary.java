package com.searchforge.query.synonym.dictionary;

import com.searchforge.query.synonym.config.SynonymExpansionMode;
import com.searchforge.query.synonym.config.SynonymProperties;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory synonym dictionary.
 *
 * Maintains decoupled tables for unidirectional and bidirectional synonym associations,
 * enforces lowercase normalization, and prevents self-referential mappings.
 */
@Component
public class InMemorySynonymDictionary implements SynonymDictionary {

    private final ConcurrentMap<String, Set<String>> oneWayMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> bidirectionalMap = new ConcurrentHashMap<>();

    public InMemorySynonymDictionary(SynonymProperties properties) {
        if (properties != null) {
            // Load pre-configured one-way mappings
            if (properties.getOneWay() != null) {
                properties.getOneWay().forEach(this::addOneWay);
            }
            // Load pre-configured bidirectional mappings
            if (properties.getBidirectional() != null) {
                properties.getBidirectional().forEach(this::addBidirectional);
            }
        }
    }

    public InMemorySynonymDictionary() {
        this(null);
    }

    @Override
    public void addOneWay(String from, String to) {
        if (from == null || to == null) return;
        addOneWay(from, List.of(to));
    }

    @Override
    public void addOneWay(String from, Collection<String> synonyms) {
        if (from == null || from.isBlank() || synonyms == null || synonyms.isEmpty()) {
            return;
        }

        String normFrom = from.trim().toLowerCase(Locale.ROOT);
        Set<String> targetSet = oneWayMap.computeIfAbsent(normFrom, k -> ConcurrentHashMap.newKeySet());

        for (String syn : synonyms) {
            if (syn != null && !syn.isBlank()) {
                String normSyn = syn.trim().toLowerCase(Locale.ROOT);
                if (!normSyn.equals(normFrom)) { // Prevent self-mapping
                    targetSet.add(normSyn);
                }
            }
        }
    }

    @Override
    public void addBidirectional(String term1, String term2) {
        if (term1 == null || term2 == null) return;
        addBidirectional(term1, List.of(term2));
    }

    @Override
    public void addBidirectional(String term, Collection<String> synonyms) {
        if (term == null || term.isBlank() || synonyms == null || synonyms.isEmpty()) {
            return;
        }

        String normTerm = term.trim().toLowerCase(Locale.ROOT);

        for (String syn : synonyms) {
            if (syn != null && !syn.isBlank()) {
                String normSyn = syn.trim().toLowerCase(Locale.ROOT);
                if (!normSyn.equals(normTerm)) {
                    // Register both directions: term -> syn AND syn -> term
                    bidirectionalMap.computeIfAbsent(normTerm, k -> ConcurrentHashMap.newKeySet()).add(normSyn);
                    bidirectionalMap.computeIfAbsent(normSyn, k -> ConcurrentHashMap.newKeySet()).add(normTerm);
                }
            }
        }
    }

    @Override
    public Set<String> getSynonyms(String term, SynonymExpansionMode mode) {
        if (term == null || term.isBlank() || mode == null || mode == SynonymExpansionMode.DISABLED) {
            return Collections.emptySet();
        }

        String norm = term.trim().toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();

        // In ONE_WAY mode, return only unidirectional mappings
        Set<String> oneWay = oneWayMap.get(norm);
        if (oneWay != null) {
            result.addAll(oneWay);
        }

        // In BIDIRECTIONAL mode, return both bidirectional and one-way mappings
        if (mode == SynonymExpansionMode.BIDIRECTIONAL) {
            Set<String> bi = bidirectionalMap.get(norm);
            if (bi != null) {
                result.addAll(bi);
            }
        }

        // Ensure term is never in its own synonym set
        result.remove(norm);

        return Collections.unmodifiableSet(result);
    }

    @Override
    public boolean hasSynonyms(String term, SynonymExpansionMode mode) {
        return !getSynonyms(term, mode).isEmpty();
    }

    @Override
    public int size() {
        Set<String> allKeys = new HashSet<>(oneWayMap.keySet());
        allKeys.addAll(bidirectionalMap.keySet());
        return allKeys.size();
    }

    @Override
    public void clear() {
        oneWayMap.clear();
        bidirectionalMap.clear();
    }
}
