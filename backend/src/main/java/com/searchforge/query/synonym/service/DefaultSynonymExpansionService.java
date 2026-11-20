package com.searchforge.query.synonym.service;

import com.searchforge.indexer.token.StopWordFilter;
import com.searchforge.indexer.token.TextNormalizer;
import com.searchforge.query.synonym.config.SynonymExpansionMode;
import com.searchforge.query.synonym.config.SynonymProperties;
import com.searchforge.query.synonym.dictionary.SynonymDictionary;
import com.searchforge.query.synonym.model.ExpandedQuery;
import com.searchforge.query.synonym.model.ExpandedTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production implementation of SynonymExpansionService.
 *
 * Implements configurable query expansion, weighted discount scoring for synonyms,
 * and comprehensive safeguards against circular expansions, duplicate terms, stop-word
 * pollution, and query explosion.
 */
@Service
public class DefaultSynonymExpansionService implements SynonymExpansionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSynonymExpansionService.class);
    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final SynonymDictionary dictionary;
    private final SynonymProperties properties;
    private final StopWordFilter stopWordFilter;

    @Autowired
    public DefaultSynonymExpansionService(SynonymDictionary dictionary,
                                         SynonymProperties properties,
                                         StopWordFilter stopWordFilter) {
        this.dictionary = Objects.requireNonNull(dictionary, "SynonymDictionary cannot be null");
        this.properties = Objects.requireNonNull(properties, "SynonymProperties cannot be null");
        this.stopWordFilter = stopWordFilter != null ? stopWordFilter : new StopWordFilter();
    }

    public DefaultSynonymExpansionService(SynonymDictionary dictionary, SynonymProperties properties) {
        this(dictionary, properties, new StopWordFilter());
    }

    @Override
    public ExpandedQuery expandQuery(String query) {
        if (query == null || query.isBlank()) {
            return new ExpandedQuery(query != null ? query : "", List.of(), false);
        }

        List<String> rawTerms = extractTerms(query);
        if (rawTerms.isEmpty()) {
            return new ExpandedQuery(query, List.of(), false);
        }

        SynonymExpansionMode mode = properties.getMode();
        if (mode == SynonymExpansionMode.DISABLED) {
            return ExpandedQuery.unexpanded(query, rawTerms);
        }

        List<ExpandedTerm> expandedTerms = expandTerms(rawTerms);
        boolean hasAnySynonym = expandedTerms.stream().anyMatch(t -> !t.isOriginal());

        return new ExpandedQuery(query, expandedTerms, hasAnySynonym);
    }

    @Override
    public List<ExpandedTerm> expandTerms(Collection<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }

        SynonymExpansionMode mode = properties.getMode();
        double discount = properties.getDiscount();
        int maxSynonyms = properties.getMaxSynonymsPerTerm();

        // Track terms to prevent duplicate additions or circular re-expansion
        Set<String> seenTerms = new HashSet<>();
        List<ExpandedTerm> originalTerms = new ArrayList<>();
        List<ExpandedTerm> synonymTerms = new ArrayList<>();

        // First pass: register all original query terms with priority weight 1.0
        for (String rawTerm : terms) {
            if (rawTerm == null || rawTerm.isBlank()) {
                continue;
            }
            String norm = rawTerm.trim().toLowerCase(Locale.ROOT);
            if (seenTerms.add(norm)) {
                originalTerms.add(new ExpandedTerm(norm, 1.0, true));
            }
        }

        // Second pass: if mode is not DISABLED, expand synonyms with discounted weight
        if (mode != SynonymExpansionMode.DISABLED) {
            for (ExpandedTerm orig : originalTerms) {
                Set<String> synonyms = dictionary.getSynonyms(orig.term(), mode);
                int count = 0;

                for (String syn : synonyms) {
                    if (count >= maxSynonyms) {
                        break;
                    }

                    String normSyn = syn.trim().toLowerCase(Locale.ROOT);

                    // Safeguards:
                    // 1. Skip if already seen (prevents circular loops and duplicate expansions)
                    // 2. Skip stop words
                    // 3. Skip empty strings
                    if (!normSyn.isEmpty()
                            && !seenTerms.contains(normSyn)
                            && !stopWordFilter.isStopWord(normSyn)) {

                        seenTerms.add(normSyn);
                        synonymTerms.add(new ExpandedTerm(normSyn, discount, false));
                        count++;
                    }
                }
            }
        }

        // Combine: original terms first, followed by discounted synonyms
        List<ExpandedTerm> result = new ArrayList<>(originalTerms.size() + synonymTerms.size());
        result.addAll(originalTerms);
        result.addAll(synonymTerms);

        return Collections.unmodifiableList(result);
    }

    @Override
    public Set<String> getSynonymsForTerm(String term) {
        if (term == null || term.isBlank()) {
            return Collections.emptySet();
        }
        return dictionary.getSynonyms(term, properties.getMode());
    }

    @Override
    public SynonymExpansionMode getMode() {
        return properties.getMode();
    }

    @Override
    public double getSynonymDiscount() {
        return properties.getDiscount();
    }

    private List<String> extractTerms(String query) {
        List<String> terms = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(query);
        while (matcher.find()) {
            String word = matcher.group();
            String norm = TextNormalizer.normalize(word);
            if (!norm.isEmpty() && !stopWordFilter.isStopWord(norm)) {
                terms.add(norm.toLowerCase(Locale.ROOT));
            }
        }
        return terms;
    }
}
