package com.searchforge.query.spelling.service;

import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.TermMetadata;
import com.searchforge.query.spelling.algorithm.DamerauLevenshteinDistance;
import com.searchforge.query.spelling.dto.QuerySuggestionResponse;
import com.searchforge.query.spelling.model.CandidateMatch;
import com.searchforge.query.spelling.model.SpellingCorrection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production implementation of SpellingCorrectionService.
 *
 * Implements candidate generation from the inverted index vocabulary using
 * length-bucket pruning, Damerau-Levenshtein edit distance filtering, and
 * a Noisy Channel ranking model combining edit penalties with corpus frequencies.
 */
@Service
public class DefaultSpellingCorrectionService implements SpellingCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSpellingCorrectionService.class);

    private static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int DEFAULT_MAX_EDIT_DISTANCE = 2;

    private final InvertedIndex invertedIndex;
    private final int maxEditDistance;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultSpellingCorrectionService(InvertedIndex invertedIndex,
                                           @Value("${search.spelling.max-edit-distance:2}") int maxEditDistance) {
        this.invertedIndex = Objects.requireNonNull(invertedIndex, "InvertedIndex cannot be null");
        this.maxEditDistance = maxEditDistance > 0 ? maxEditDistance : DEFAULT_MAX_EDIT_DISTANCE;
    }

    public DefaultSpellingCorrectionService(InvertedIndex invertedIndex) {
        this(invertedIndex, DEFAULT_MAX_EDIT_DISTANCE);
    }

    @Override
    public QuerySuggestionResponse suggest(String query) {
        if (query == null || query.isBlank()) {
            return QuerySuggestionResponse.noCorrection(query != null ? query : "");
        }

        Map<String, TermMetadata> vocabulary = invertedIndex.getDictionaryMetadata();
        if (vocabulary == null || vocabulary.isEmpty()) {
            return QuerySuggestionResponse.noCorrection(query);
        }

        Matcher matcher = WORD_PATTERN.matcher(query);
        StringBuilder suggestedQuery = new StringBuilder();
        List<SpellingCorrection> corrections = new ArrayList<>();
        int lastEnd = 0;
        boolean hasAnyCorrection = false;

        while (matcher.find()) {
            // Append non-word interstitial text (spaces, punctuation)
            suggestedQuery.append(query, lastEnd, matcher.start());

            String originalWord = matcher.group();
            String normalizedWord = originalWord.toLowerCase(Locale.ROOT);

            // Rule: Do not modify terms that already exist in the indexed vocabulary
            if (vocabulary.containsKey(normalizedWord)) {
                suggestedQuery.append(originalWord);
            } else {
                Optional<SpellingCorrection> correctionOpt = findBestCorrection(normalizedWord, vocabulary);
                if (correctionOpt.isPresent()) {
                    SpellingCorrection correction = correctionOpt.get();
                    corrections.add(new SpellingCorrection(originalWord, correction.suggestion(), correction.editDistance()));
                    suggestedQuery.append(matchCase(originalWord, correction.suggestion()));
                    hasAnyCorrection = true;
                } else {
                    suggestedQuery.append(originalWord);
                }
            }

            lastEnd = matcher.end();
        }

        // Append any trailing characters
        suggestedQuery.append(query.substring(lastEnd));

        return new QuerySuggestionResponse(
                query,
                suggestedQuery.toString(),
                hasAnyCorrection,
                corrections
        );
    }

    @Override
    public Optional<SpellingCorrection> correctTerm(String term) {
        if (term == null || term.isBlank()) {
            return Optional.empty();
        }

        String normalized = term.trim().toLowerCase(Locale.ROOT);
        Map<String, TermMetadata> vocabulary = invertedIndex.getDictionaryMetadata();
        if (vocabulary == null || vocabulary.isEmpty() || vocabulary.containsKey(normalized)) {
            return Optional.empty();
        }

        return findBestCorrection(normalized, vocabulary);
    }

    @Override
    public int getMaxEditDistance() {
        return maxEditDistance;
    }

    /**
     * Finds the best candidate term within maxEditDistance using Noisy Channel ranking.
     */
    private Optional<SpellingCorrection> findBestCorrection(String normalizedWord, Map<String, TermMetadata> vocabulary) {
        int wordLen = normalizedWord.length();
        List<CandidateMatch> candidates = new ArrayList<>();

        for (TermMetadata meta : vocabulary.values()) {
            String candidateTerm = meta.term();
            int candLen = candidateTerm.length();

            // Length-filtering pruning: edit distance is at least |wordLen - candLen|
            if (Math.abs(wordLen - candLen) > maxEditDistance) {
                continue;
            }

            int distance = DamerauLevenshteinDistance.calculate(normalizedWord, candidateTerm, maxEditDistance);
            if (distance <= maxEditDistance) {
                double score = computeNoisyChannelScore(distance, meta.totalTermFrequency(), meta.documentFrequency());
                candidates.add(new CandidateMatch(candidateTerm, distance, score));
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Sort candidates: score DESC, term ASC
        Collections.sort(candidates);
        CandidateMatch best = candidates.get(0);

        return Optional.of(new SpellingCorrection(normalizedWord, best.term(), best.editDistance()));
    }

    /**
     * Noisy Channel ranking approximation:
     * P(candidate | typo) proportional to P(typo | candidate) * P(candidate)
     *
     * 1. Error model P(typo | candidate):
     *    - Distance 1: weight 10.0
     *    - Distance 2: weight 1.0 (heavily penalizes 2 edits over 1 edit)
     * 2. Language model P(candidate):
     *    - Evaluated from collection statistics: ln(1 + ctf) * sqrt(df) + 1.0
     */
    public static double computeNoisyChannelScore(int distance, long ctf, int df) {
        double errorWeight = (distance <= 1) ? 10.0 : 1.0;
        double languageModelPrior = Math.log(1.0 + Math.max(0, ctf)) * Math.sqrt(Math.max(1, df)) + 1.0;
        return errorWeight * languageModelPrior;
    }

    /**
     * Preserves original word casing (e.g., "Postgreql" -> "Postgresql").
     */
    private static String matchCase(String original, String suggestion) {
        if (original.isEmpty() || suggestion.isEmpty()) {
            return suggestion;
        }
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(suggestion.charAt(0)) + suggestion.substring(1);
        }
        return suggestion;
    }
}
