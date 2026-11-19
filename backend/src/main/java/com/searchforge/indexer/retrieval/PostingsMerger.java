package com.searchforge.indexer.retrieval;

import java.util.*;

/**
 * High-performance set operations for sorted postings lists using two-pointer linear merge algorithms.
 * All methods execute in O(L1 + L2) time and preserve ascending documentId order.
 */
public final class PostingsMerger {

    private PostingsMerger() {
    }

    /**
     * Intersects two sorted postings lists (AND logic) in O(L1 + L2) time.
     */
    public static List<Long> intersect(List<Long> p1, List<Long> p2) {
        if (p1 == null || p2 == null || p1.isEmpty() || p2.isEmpty()) {
            return List.of();
        }

        List<Long> result = new ArrayList<>();
        int i = 0, j = 0;
        int s1 = p1.size(), s2 = p2.size();

        while (i < s1 && j < s2) {
            long d1 = p1.get(i);
            long d2 = p2.get(j);
            if (d1 == d2) {
                result.add(d1);
                i++;
                j++;
            } else if (d1 < d2) {
                i++;
            } else {
                j++;
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Unions two sorted postings lists (OR logic) in O(L1 + L2) time.
     */
    public static List<Long> union(List<Long> p1, List<Long> p2) {
        if (p1 == null || p1.isEmpty()) {
            return p2 != null ? List.copyOf(p2) : List.of();
        }
        if (p2 == null || p2.isEmpty()) {
            return List.copyOf(p1);
        }

        List<Long> result = new ArrayList<>();
        int i = 0, j = 0;
        int s1 = p1.size(), s2 = p2.size();

        while (i < s1 && j < s2) {
            long d1 = p1.get(i);
            long d2 = p2.get(j);
            if (d1 == d2) {
                result.add(d1);
                i++;
                j++;
            } else if (d1 < d2) {
                result.add(d1);
                i++;
            } else {
                result.add(d2);
                j++;
            }
        }
        while (i < s1) result.add(p1.get(i++));
        while (j < s2) result.add(p2.get(j++));

        return Collections.unmodifiableList(result);
    }

    /**
     * Computes the difference (p1 AND NOT p2) in O(L1 + L2) time.
     * Returns documents present in p1 but absent in p2.
     */
    public static List<Long> difference(List<Long> p1, List<Long> p2) {
        if (p1 == null || p1.isEmpty()) {
            return List.of();
        }
        if (p2 == null || p2.isEmpty()) {
            return List.copyOf(p1);
        }

        List<Long> result = new ArrayList<>();
        int i = 0, j = 0;
        int s1 = p1.size(), s2 = p2.size();

        while (i < s1 && j < s2) {
            long d1 = p1.get(i);
            long d2 = p2.get(j);
            if (d1 == d2) {
                i++;
                j++;
            } else if (d1 < d2) {
                result.add(d1);
                i++;
            } else {
                j++;
            }
        }
        while (i < s1) result.add(p1.get(i++));

        return Collections.unmodifiableList(result);
    }

    /**
     * Optimally intersects multiple postings lists by sorting them by size ascending
     * (document frequency optimization) before merging.
     */
    public static List<Long> intersectAll(List<List<Long>> postingsLists) {
        if (postingsLists == null || postingsLists.isEmpty()) {
            return List.of();
        }
        if (postingsLists.size() == 1) {
            return postingsLists.get(0) != null ? List.copyOf(postingsLists.get(0)) : List.of();
        }

        List<List<Long>> nonNull = postingsLists.stream()
                .filter(Objects::nonNull)
                .toList();

        if (nonNull.size() < postingsLists.size()) {
            return List.of();
        }

        List<List<Long>> sorted = new ArrayList<>(nonNull);
        sorted.sort(Comparator.comparingInt(List::size));

        List<Long> current = sorted.get(0);
        for (int k = 1; k < sorted.size(); k++) {
            if (current.isEmpty()) {
                return List.of();
            }
            current = intersect(current, sorted.get(k));
        }
        return current;
    }

    /**
     * Unions multiple postings lists sequentially.
     */
    public static List<Long> unionAll(List<List<Long>> postingsLists) {
        if (postingsLists == null || postingsLists.isEmpty()) {
            return List.of();
        }
        List<Long> current = List.of();
        for (List<Long> list : postingsLists) {
            if (list != null && !list.isEmpty()) {
                current = union(current, list);
            }
        }
        return current;
    }
}
