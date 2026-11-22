package com.searchforge.lifecycle.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Thread-safe fine-grained document lock coordinator.
 *
 * Provides striping locks keyed by document ID to ensure that concurrent operations
 * on the SAME document (such as staged indexing, update, or deletion) execute
 * under mutual exclusion, while operations on DIFFERENT documents execute in parallel.
 *
 * Employs atomic reference-counting on lock holders so that inactive locks
 * are automatically pruned from memory, preventing memory leaks during large crawls.
 */
@Component
public class DocumentLockManager {

    private static final Logger log = LoggerFactory.getLogger(DocumentLockManager.class);

    private static class LockHolder {
        final ReentrantLock lock = new ReentrantLock(true); // Fair lock to prevent worker starvation
        final AtomicInteger refCount = new AtomicInteger(1);
    }

    private final Map<Long, LockHolder> locks = new ConcurrentHashMap<>();

    /**
     * Executes the given action under an exclusive lock for the specified document ID.
     *
     * @param documentId Document ID to lock.
     * @param action     Supplier action to execute under the lock.
     * @param <T>        Return type.
     * @return Result of the action.
     */
    public <T> T withLock(Long documentId, Supplier<T> action) {
        Objects.requireNonNull(documentId, "documentId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        LockHolder holder = acquireHolder(documentId);
        holder.lock.lock();
        try {
            return action.get();
        } finally {
            holder.lock.unlock();
            releaseHolder(documentId, holder);
        }
    }

    /**
     * Executes the given runnable under an exclusive lock for the specified document ID.
     *
     * @param documentId Document ID to lock.
     * @param action     Runnable action to execute under the lock.
     */
    public void withLock(Long documentId, Runnable action) {
        withLock(documentId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Attempts to acquire the document lock within the given timeout before executing.
     *
     * @param documentId Document ID to lock.
     * @param timeoutMs  Maximum milliseconds to wait for the lock.
     * @param action     Supplier action to execute if lock is acquired.
     * @param <T>        Return type.
     * @return Result of action if acquired, or empty if timed out.
     */
    public <T> T withLockTimeout(Long documentId, long timeoutMs, Supplier<T> action) {
        Objects.requireNonNull(documentId, "documentId cannot be null");
        Objects.requireNonNull(action, "action cannot be null");

        LockHolder holder = acquireHolder(documentId);
        boolean acquired = false;
        try {
            acquired = holder.lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new IllegalStateException("Timed out after " + timeoutMs + "ms acquiring lock for docId " + documentId);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock on docId " + documentId, e);
        } finally {
            if (acquired) {
                holder.lock.unlock();
            }
            releaseHolder(documentId, holder);
        }
    }

    private LockHolder acquireHolder(Long documentId) {
        return locks.compute(documentId, (id, existing) -> {
            if (existing == null) {
                return new LockHolder();
            }
            existing.refCount.incrementAndGet();
            return existing;
        });
    }

    private void releaseHolder(Long documentId, LockHolder holder) {
        locks.compute(documentId, (id, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.refCount.decrementAndGet() <= 0) {
                return null; // Prune memory cleanly
            }
            return existing;
        });
    }

    /**
     * Returns true if the specified document is currently locked.
     */
    public boolean isLocked(Long documentId) {
        if (documentId == null) {
            return false;
        }
        LockHolder holder = locks.get(documentId);
        return holder != null && holder.lock.isLocked();
    }

    /**
     * Returns the count of documents currently actively locked or queued for locking.
     */
    public int getActiveLockCount() {
        return locks.size();
    }

    /**
     * Clears all locks (for testing purposes).
     */
    public void clear() {
        locks.clear();
    }
}
