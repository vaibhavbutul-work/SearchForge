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
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-performance in-memory implementation of the SearchForge Inverted Index.
 *
 * Thread-safe: Uses ReentrantReadWriteLock to coordinate readers and writers,
 * ensuring atomic compound document additions and removals, and preventing
 * partial document state visibility (such as zero document length during removal).
 *
 * Implements atomic document replacement and point-in-time query snapshots to
 * provide strict read-isolation for concurrent search queries without multi-read skew.
 */
@Component
public class InMemoryInvertedIndex implements InvertedIndex {

    private final ConcurrentMap<String, PostingsList> dictionary = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Integer> docLengths = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Set<String>> docTerms = new ConcurrentHashMap<>();
    private final AtomicLong totalTokens = new AtomicLong(0);

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    @Override
    public void addDocument(long documentId, List<ProcessedToken> tokens) {
        writeLock.lock();
        try {
            if (docLengths.containsKey(documentId)) {
                replaceDocument(documentId, tokens);
                return;
            }

            if (tokens == null || tokens.isEmpty()) {
                docLengths.put(documentId, 0);
                docTerms.put(documentId, Collections.emptySet());
                return;
            }

            int tokenCount = tokens.size();
            docLengths.put(documentId, tokenCount);
            totalTokens.addAndGet(tokenCount);

            // Group token positions by term for this document
            Map<String, List<Integer>> termPositions = new HashMap<>();
            for (ProcessedToken token : tokens) {
                termPositions.computeIfAbsent(token.term(), k -> new ArrayList<>()).add(token.position());
            }

            docTerms.put(documentId, new HashSet<>(termPositions.keySet()));

            // Invert and append postings to dictionary
            for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
                String term = entry.getKey();
                List<Integer> positions = entry.getValue();

                PostingsList postingsList = dictionary.computeIfAbsent(term, k -> new PostingsList());
                postingsList.putPosting(new Posting(documentId, positions.size(), positions));
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void replaceDocument(long documentId, List<ProcessedToken> tokens) {
        writeLock.lock();
        try {
            if (tokens == null || tokens.isEmpty()) {
                removeDocument(documentId);
                return;
            }

            int newTokenCount = tokens.size();
            Integer previousLength = docLengths.put(documentId, newTokenCount);
            if (previousLength != null) {
                totalTokens.addAndGet(newTokenCount - previousLength);
            } else {
                totalTokens.addAndGet(newTokenCount);
            }

            // Group new token positions by term
            Map<String, List<Integer>> newTermPositions = new HashMap<>();
            for (ProcessedToken token : tokens) {
                newTermPositions.computeIfAbsent(token.term(), k -> new ArrayList<>()).add(token.position());
            }

            Set<String> newTerms = newTermPositions.keySet();
            Set<String> oldTerms = docTerms.put(documentId, new HashSet<>(newTerms));

            // Remove postings from terms that were present previously but no longer in the new version
            if (oldTerms != null) {
                for (String oldTerm : oldTerms) {
                    if (!newTerms.contains(oldTerm)) {
                        PostingsList pl = dictionary.get(oldTerm);
                        if (pl != null) {
                            pl.removePosting(documentId);
                            if (pl.size() == 0) {
                                dictionary.remove(oldTerm, pl);
                            }
                        }
                    }
                }
            }

            // Insert or update postings for all terms in the new version
            for (Map.Entry<String, List<Integer>> entry : newTermPositions.entrySet()) {
                String term = entry.getKey();
                List<Integer> positions = entry.getValue();

                PostingsList postingsList = dictionary.computeIfAbsent(term, k -> new PostingsList());
                postingsList.putPosting(new Posting(documentId, positions.size(), positions));
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public IndexQuerySnapshot getSnapshot(Collection<String> queryTerms) {
        readLock.lock();
        try {
            long totalDocs = docLengths.size();
            double avgDl = totalDocs > 0 ? (double) totalTokens.get() / totalDocs : 0.0;

            if (queryTerms == null || queryTerms.isEmpty()) {
                return new IndexQuerySnapshot(totalDocs, avgDl, Collections.emptyMap(), Collections.emptyMap());
            }

            Map<String, PostingsList> snapshotPostings = new HashMap<>();
            Map<Long, Integer> candidateDocLengths = new HashMap<>();

            for (String term : queryTerms) {
                if (term == null) continue;
                PostingsList pl = dictionary.get(term);
                if (pl != null) {
                    PostingsList snapshotList = pl.snapshot();
                    snapshotPostings.put(term, snapshotList);
                    snapshotList.forEachPosting(p -> {
                        long docId = p.documentId();
                        candidateDocLengths.computeIfAbsent(docId, id -> docLengths.getOrDefault(id, 0));
                    });
                }
            }

            return new IndexQuerySnapshot(totalDocs, avgDl, snapshotPostings, candidateDocLengths);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Optional<PostingsList> getPostings(String term) {
        if (term == null) {
            return Optional.empty();
        }
        readLock.lock();
        try {
            return Optional.ofNullable(dictionary.get(term));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int getDocLength(long documentId) {
        readLock.lock();
        try {
            return docLengths.getOrDefault(documentId, 0);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public int getTotalDocuments() {
        readLock.lock();
        try {
            return docLengths.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<Long> getAllDocumentIds() {
        readLock.lock();
        try {
            List<Long> ids = new ArrayList<>(docLengths.keySet());
            Collections.sort(ids);
            return Collections.unmodifiableList(ids);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public double getAverageDocumentLength() {
        readLock.lock();
        try {
            int n = docLengths.size();
            return n > 0 ? (double) totalTokens.get() / n : 0.0;
        } finally {
            readLock.unlock();
        }
    }

    public long getTotalTokens() {
        return totalTokens.get();
    }

    public int getDocumentCount() {
        return getTotalDocuments();
    }

    @Override
    public int getVocabularySize() {
        readLock.lock();
        try {
            return dictionary.size();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Map<String, TermMetadata> getDictionaryMetadata() {
        readLock.lock();
        try {
            Map<String, TermMetadata> metadataMap = new HashMap<>();
            for (Map.Entry<String, PostingsList> entry : dictionary.entrySet()) {
                String term = entry.getKey();
                PostingsList pl = entry.getValue();
                metadataMap.put(term, new TermMetadata(term, pl.size(), pl.getTotalTermFrequency()));
            }
            return Collections.unmodifiableMap(metadataMap);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            dictionary.clear();
            docLengths.clear();
            docTerms.clear();
            totalTokens.set(0);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeDocument(long documentId) {
        writeLock.lock();
        try {
            Set<String> terms = docTerms.remove(documentId);
            if (terms != null) {
                for (String term : terms) {
                    PostingsList pl = dictionary.get(term);
                    if (pl != null) {
                        pl.removePosting(documentId);
                        if (pl.size() == 0) {
                            dictionary.remove(term, pl);
                        }
                    }
                }
            } else {
                dictionary.forEach((term, postingsList) -> {
                    postingsList.removePosting(documentId);
                    if (postingsList.size() == 0) {
                        dictionary.remove(term, postingsList);
                    }
                });
            }

            Integer length = docLengths.remove(documentId);
            if (length != null) {
                totalTokens.addAndGet(-length);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void addDocumentPostings(long documentId, int docLength, Map<String, List<Integer>> termPositions) {
        writeLock.lock();
        try {
            Integer previousLength = docLengths.put(documentId, docLength);
            if (previousLength != null) {
                totalTokens.addAndGet(docLength - previousLength);
            } else {
                totalTokens.addAndGet(docLength);
            }

            if (termPositions == null || termPositions.isEmpty()) {
                docTerms.put(documentId, Collections.emptySet());
                return;
            }

            docTerms.put(documentId, new HashSet<>(termPositions.keySet()));

            for (Map.Entry<String, List<Integer>> entry : termPositions.entrySet()) {
                String term = entry.getKey();
                List<Integer> positions = entry.getValue();
                if (positions != null && !positions.isEmpty()) {
                    PostingsList postingsList = dictionary.computeIfAbsent(term, k -> new PostingsList());
                    postingsList.putPosting(new Posting(documentId, positions.size(), positions));
                }
            }
        } finally {
            writeLock.unlock();
        }
    }
}
