package com.searchforge.indexer.search;

import com.searchforge.indexer.index.InvertedIndex;
import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.token.ProcessedToken;
import com.searchforge.indexer.token.Tokenizer;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * High-performance positional search engine evaluating exact phrase queries and proximity searches.
 */
@Component
public class PositionalIndexSearcher {

    private final InvertedIndex invertedIndex;
    private final Tokenizer tokenizer;

    public PositionalIndexSearcher(InvertedIndex invertedIndex, Tokenizer tokenizer) {
        this.invertedIndex = invertedIndex;
        this.tokenizer = tokenizer;
    }

    /**
     * Executes an exact phrase search for a sequence of terms.
     *
     * @param phraseQuery Raw phrase query (e.g. "computer science", "fast search engine").
     * @return List of matching documents sorted by match count descending, then docId ascending.
     */
    public List<PhraseMatch> searchPhrase(String phraseQuery) {
        if (phraseQuery == null || phraseQuery.isBlank()) {
            return List.of();
        }

        List<ProcessedToken> tokens = tokenizer.tokenize(phraseQuery);
        if (tokens.isEmpty()) {
            return List.of();
        }

        if (tokens.size() == 1) {
            String term = tokens.get(0).term();
            Optional<PostingsList> postingsOpt = invertedIndex.getPostings(term);
            if (postingsOpt.isEmpty()) {
                return List.of();
            }
            List<PhraseMatch> matches = new ArrayList<>();
            for (Posting p : postingsOpt.get()) {
                matches.add(new PhraseMatch(p.documentId(), p.termFrequency(), p.positions()));
            }
            Collections.sort(matches);
            return matches;
        }

        // Multi-word phrase positional evaluation
        int basePos = tokens.get(0).position();
        List<String> terms = new ArrayList<>();
        List<Integer> relativeOffsets = new ArrayList<>();

        for (ProcessedToken token : tokens) {
            terms.add(token.term());
            relativeOffsets.add(token.position() - basePos);
        }

        // Retrieve postings lists for all terms
        List<PostingsList> postingsLists = new ArrayList<>();
        for (String term : terms) {
            Optional<PostingsList> plOpt = invertedIndex.getPostings(term);
            if (plOpt.isEmpty() || plOpt.get().size() == 0) {
                // If any term has zero postings, the phrase cannot match anywhere
                return List.of();
            }
            postingsLists.add(plOpt.get());
        }

        // Intersect candidate document IDs that contain ALL terms
        List<Long> candidateDocIds = findCandidateDocuments(postingsLists);
        if (candidateDocIds.isEmpty()) {
            return List.of();
        }

        List<PhraseMatch> results = new ArrayList<>();

        for (long docId : candidateDocIds) {
            List<List<Integer>> termDocPositions = new ArrayList<>();
            for (PostingsList pl : postingsLists) {
                Optional<Posting> pOpt = pl.getPostingForDocument(docId);
                if (pOpt.isEmpty()) {
                    termDocPositions.clear();
                    break;
                }
                termDocPositions.add(pOpt.get().positions());
            }

            if (termDocPositions.size() != terms.size()) {
                continue;
            }

            List<Integer> firstTermPositions = termDocPositions.get(0);
            List<Integer> validPhraseStarts = new ArrayList<>();

            for (int startPos : firstTermPositions) {
                boolean allMatch = true;
                for (int i = 1; i < terms.size(); i++) {
                    int expectedPos = startPos + relativeOffsets.get(i);
                    List<Integer> positions = termDocPositions.get(i);
                    if (Collections.binarySearch(positions, expectedPos) < 0) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    validPhraseStarts.add(startPos);
                }
            }

            if (!validPhraseStarts.isEmpty()) {
                results.add(new PhraseMatch(docId, validPhraseStarts.size(), validPhraseStarts));
            }
        }

        Collections.sort(results);
        return results;
    }

    /**
     * Helper returning just matching document IDs for an exact phrase query.
     */
    public List<Long> searchPhraseDocIds(String phraseQuery) {
        return searchPhrase(phraseQuery).stream()
                .map(PhraseMatch::documentId)
                .toList();
    }

    /**
     * Evaluates proximity search between two terms within maxDistance word positions.
     *
     * @param term1       First term.
     * @param term2       Second term.
     * @param maxDistance Maximum distance in word positions (e.g. 3).
     * @return List of matching documents.
     */
    public List<PhraseMatch> searchProximity(String term1, String term2, int maxDistance) {
        if (term1 == null || term2 == null || maxDistance <= 0) {
            return List.of();
        }

        List<ProcessedToken> tokens1 = tokenizer.tokenize(term1);
        List<ProcessedToken> tokens2 = tokenizer.tokenize(term2);
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return List.of();
        }

        String t1 = tokens1.get(0).term();
        String t2 = tokens2.get(0).term();

        Optional<PostingsList> pl1 = invertedIndex.getPostings(t1);
        Optional<PostingsList> pl2 = invertedIndex.getPostings(t2);
        if (pl1.isEmpty() || pl2.isEmpty()) {
            return List.of();
        }

        List<Long> candidates = findCandidateDocuments(List.of(pl1.get(), pl2.get()));
        List<PhraseMatch> results = new ArrayList<>();

        for (long docId : candidates) {
            Optional<Posting> p1Opt = pl1.get().getPostingForDocument(docId);
            Optional<Posting> p2Opt = pl2.get().getPostingForDocument(docId);
            if (p1Opt.isEmpty() || p2Opt.isEmpty()) {
                continue;
            }

            List<Integer> posList1 = p1Opt.get().positions();
            List<Integer> posList2 = p2Opt.get().positions();
            List<Integer> matchingStarts = new ArrayList<>();

            // Two-pointer proximity search
            int i = 0;
            int j = 0;
            while (i < posList1.size() && j < posList2.size()) {
                int p1 = posList1.get(i);
                int p2 = posList2.get(j);
                int diff = Math.abs(p1 - p2);

                if (diff > 0 && diff <= maxDistance) {
                    matchingStarts.add(Math.min(p1, p2));
                }

                if (p1 < p2) {
                    i++;
                } else {
                    j++;
                }
            }

            if (!matchingStarts.isEmpty()) {
                results.add(new PhraseMatch(docId, matchingStarts.size(), matchingStarts));
            }
        }

        Collections.sort(results);
        return results;
    }

    /**
     * Intersects postings lists to find document IDs common to all given terms in linear time.
     */
    private List<Long> findCandidateDocuments(List<PostingsList> postingsLists) {
        if (postingsLists.isEmpty()) {
            return List.of();
        }

        // Start with the document IDs of the smallest postings list for fast pruning
        PostingsList smallest = postingsLists.stream()
                .min(Comparator.comparingInt(PostingsList::size))
                .orElse(postingsLists.get(0));

        List<Long> candidates = new ArrayList<>();
        for (Posting p : smallest) {
            candidates.add(p.documentId());
        }

        for (PostingsList pl : postingsLists) {
            if (pl == smallest) {
                continue;
            }
            candidates.removeIf(docId -> !pl.containsDocument(docId));
            if (candidates.isEmpty()) {
                break;
            }
        }

        return candidates;
    }
}
