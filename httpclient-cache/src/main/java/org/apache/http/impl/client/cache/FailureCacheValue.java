package org.apache.http.impl.client.cache;

import org.apache.http.annotation.Immutable;

/**
 * The error count with a creation timestamp.
 */
@Immutable
public class FailureCacheValue {

    private final long creationTimeInNanos;
    private final int errorCount;

    public FailureCacheValue(int errorCount) {
        this.creationTimeInNanos = System.nanoTime();
        this.errorCount = errorCount;
    }

    public long getCreationTimeInNanos() {
        return creationTimeInNanos;
    }

    public int getErrorCount() {
        return errorCount;
    }

    @Override
    public String toString() {
        return "[entry creationTimeInNanos=" + creationTimeInNanos + "; errorCount=" + errorCount + ']';
    }
}
