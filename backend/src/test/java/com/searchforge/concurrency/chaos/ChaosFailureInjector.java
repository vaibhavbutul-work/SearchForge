package com.searchforge.concurrency.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic chaos engineering and fault injection utility.
 *
 * Simulates real-world distributed system anomalies:
 * 1. CPU / Inference memory pressure (OOM / timeout during tokenization or ONNX calculation).
 * 2. Relational database connectivity drops and optimistic locking collisions during Stage 2 commit.
 * 3. In-memory storage synchronization faults during Stage 3 RAM update.
 * 4. Thread starvation, delays, and simulated interrupts.
 */
public class ChaosFailureInjector {

    private static final Logger log = LoggerFactory.getLogger(ChaosFailureInjector.class);

    public enum FailurePoint {
        STAGE1_INFERENCE_OOM,
        STAGE2_DB_TRANSACTION_FAILURE,
        STAGE3_VECTOR_STORAGE_FAULT,
        CRAWLER_FETCH_TIMEOUT
    }

    private final AtomicInteger injectedFailureCount = new AtomicInteger(0);
    private final AtomicInteger interceptedCount = new AtomicInteger(0);
    private final Set<Long> targetedDocumentIds = ConcurrentHashMap.newKeySet();
    private final Set<String> failedOnceKeys = ConcurrentHashMap.newKeySet();

    private volatile double failureProbability = 0.0;
    private volatile boolean enabled = false;

    public ChaosFailureInjector() {}

    public ChaosFailureInjector(double failureProbability) {
        this.failureProbability = failureProbability;
        this.enabled = failureProbability > 0.0;
    }

    /**
     * Enables failure injection with a specific failure probability [0.0 - 1.0].
     */
    public void enableWithProbability(double probability) {
        this.failureProbability = Math.max(0.0, Math.min(1.0, probability));
        this.enabled = this.failureProbability > 0.0;
    }

    /**
     * Target specific document IDs for deterministic failure injection.
     */
    public void targetDocument(Long documentId) {
        if (documentId != null) {
            targetedDocumentIds.add(documentId);
            this.enabled = true;
        }
    }

    /**
     * Clears all targets and disables failure injection.
     */
    public void reset() {
        this.enabled = false;
        this.failureProbability = 0.0;
        this.targetedDocumentIds.clear();
        this.failedOnceKeys.clear();
        this.injectedFailureCount.set(0);
        this.interceptedCount.set(0);
    }

    /**
     * Injects a failure if criteria are met at the given failure point for the document.
     *
     * @param point      Lifecycle stage point.
     * @param documentId Document ID undergoing processing.
     * @param exception  Exception supplier to throw.
     */
    public void maybeInjectFailure(FailurePoint point, Long documentId, RuntimeException exception) {
        if (!enabled) {
            return;
        }

        interceptedCount.incrementAndGet();

        boolean shouldFail = false;

        // Deterministic targeting takes precedence
        if (documentId != null && targetedDocumentIds.contains(documentId)) {
            String onceKey = point.name() + ":" + documentId;
            if (failedOnceKeys.add(onceKey)) {
                shouldFail = true;
                log.info("CHAOS_INJECTOR: Injected deterministic failure at {} for doc {}", point, documentId);
            }
        } else if (failureProbability > 0.0 && Math.random() < failureProbability) {
            shouldFail = true;
            log.info("CHAOS_INJECTOR: Injected stochastic failure (p={}) at {} for doc {}",
                    failureProbability, point, documentId);
        }

        if (shouldFail) {
            injectedFailureCount.incrementAndGet();
            throw exception;
        }
    }

    public int getInjectedFailureCount() {
        return injectedFailureCount.get();
    }

    public int getInterceptedCount() {
        return interceptedCount.get();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
