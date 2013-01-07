package org.apache.http.impl.client.cache;

import org.apache.http.annotation.Immutable;

/**
 * The error count with a creation timestamp and its associated key.
 */
@Immutable
public class FailureCacheValue {

    private final long creationTimeInNanos;
    private final String key;
    private final int errorCount;

    public FailureCacheValue(String key, int errorCount) {
        this.creationTimeInNanos = System.nanoTime();
        this.key = key;
        this.errorCount = errorCount;
    }

    public long getCreationTimeInNanos() {
        return creationTimeInNanos;
    }

    public String getKey()
    {
        return key;
    }

    public int getErrorCount() {
        return errorCount;
    }

    @Override
    public String toString() {
        return "[entry creationTimeInNanos=" + creationTimeInNanos + "; key=" + key + "; errorCount=" + errorCount + ']';
    }
}
