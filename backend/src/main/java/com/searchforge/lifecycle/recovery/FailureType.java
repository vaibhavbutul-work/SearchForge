package com.searchforge.lifecycle.recovery;

/**
 * Classification of an indexing or embedding failure.
 */
public enum FailureType {
    /**
     * Intermittent issue (network blip, temporary database timeout/deadlock, memory spike)
     * where retrying is appropriate.
     */
    TRANSIENT,

    /**
     * Unrecoverable or structural defect (corrupt data, unsupported encoding, invalid schema)
     * where retrying without change will never succeed.
     */
    PERMANENT
}
