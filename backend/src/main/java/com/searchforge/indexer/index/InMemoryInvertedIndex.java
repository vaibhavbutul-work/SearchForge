package com.searchforge.indexer.index;

import com.searchforge.indexer.model.Posting;
import com.searchforge.indexer.model.PostingsList;
import com.searchforge.indexer.model.TermMetadata;
import com.searchforge.indexer.token.ProcessedToken;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance in-memory implementation of the SearchForge Inverted Index.
 *
 * Thread-safe: Uses ConcurrentHashMap for dictionary and document length mapping,
 * and synchronized PostingsList containers.
 */
@Component
public class InMemoryInvertedIndex implements InvertedIndex {

    private final ConcurrentMap<String, PostingsList> dictionary = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Integer> docLengths = new ConcurrentHashMap<>();
    private final AtomicLong totalTokens = new AtomicLong(0);

    @Override
    public void addDocument(long documentId, List<ProcessedToken> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            docLengths.put(documentId, 0);
            return;
        }

        int tokenCount = tokens.size();
        Integer previousLength = docLengths.put(documentId, tokenCount);
        if (previousLength != null) {
            totalTokens.addAndGet(tokenCount - previousLength);
        } else {
            totalTokens.addAndGet(tokenCount);
        }

        // Group token positions by term for this document
        Map<String, List<Integer>> termPositions = new HashMap<>();
        for (ProcessedToken token : tokens) {
            termPositions.computeIfAbsent(token.term(), k -> new ArrayList<>()).add(token.position());
        }

        // Invert and append postings to dictionary
        for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
            String term = entry.getKey();
            List<Integer> positions = entry.getValue();

            PostingsList postingsList = dictionary.computeIfAbsent(term, k -> new PostingsList());
            postingsList.addPosting(new Posting(documentId, positions.size(), positions));
        }
    }

    @Override
    public Optional<PostingsList> getPostings(String term) {
        if (term == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dictionary.get(term));
    }

    @Override
    public int getDocLength(long documentId) {
        return docLengths.getOrDefault(documentId, 0);
    }

    @Override
    public int getTotalDocuments() {
        return docLengths.size();
    }

    @Override
    public double getAverageDocumentLength() {
        int n = getTotalDocuments();
        return n > 0 ? (double) totalTokens.get() / n : 0.0;
    }

    @Override
    public int getVocabularySize() {
        return dictionary.size();
    }

    @Override
    public Map<String, TermMetadata> getDictionaryMetadata() {
        Map<String, TermMetadata> metadataMap = new HashMap<>();
        for (Map.Entry<String, PostingsList> entry : dictionary.entrySet()) {
            String term = entry.getKey();
            PostingsList pl = entry.getValue();
            metadataMap.put(term, new TermMetadata(term, pl.size(), pl.getTotalTermFrequency()));
        }
        return Collections.unmodifiableMap(metadataMap);
    }

    @Override
    public void clear() {
        dictionary.clear();
        docLengths.clear();
        totalTokens.set(0);
    }

    @Override
    public void removeDocument(long documentId) {
        Integer length = docLengths.remove(documentId);
        if (length != null) {
            totalTokens.addAndGet(-length);
        }

        dictionary.forEach((term, postingsList) -> {
            postingsList.removePosting(documentId);
            if (postingsList.size() == 0) {
                dictionary.remove(term, postingsList);
            }
        });
    }

    @Override
    public void addDocumentPostings(long documentId, int docLength, Map<String, List<Integer>> termPositions) {
        Integer previousLength = docLengths.put(documentId, docLength);
        if (previousLength != null) {
            totalTokens.addAndGet(docLength - previousLength);
        } else {
            totalTokens.addAndGet(docLength);
        }

        if (termPositions == null || termPositions.isEmpty()) {
            return;
        }

        for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
            String term = entry.getKey();
            List<Integer> positions = entry.getValue();
            if (positions != null && !positions.isEmpty()) {
                PostingsList postingsList = dictionary.computeIfAbsent(term, k -> new PostingsList());
                postingsList.addPosting(new Posting(documentId, positions.size(), positions));
            }
        }
    }
}
