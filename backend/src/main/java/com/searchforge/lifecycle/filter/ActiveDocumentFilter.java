package com.searchforge.lifecycle.filter;

import java.util.Collection;

/**
 * High-performance, lock-free in-memory registry determining which document IDs
 * are in the ACTIVE lifecycle state and eligible for search candidate scoring.
 *
 * Ensures search isolation during concurrent deletions and rollbacks: candidates
 * transitioning to DELETING, FAILED, or RETRY are instantaneously excluded from
 * search results in O(1) time without database queries.
 */
public interface ActiveDocumentFilter {

    boolean isActive(long documentId);

    void markActive(long documentId);

    void markInactive(long documentId);

    void initialize(Collection<Long> activeDocIds);

    int activeCount();

    void clear();
}
