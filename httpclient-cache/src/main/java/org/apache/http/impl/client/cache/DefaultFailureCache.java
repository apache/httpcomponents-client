package org.apache.http.impl.client.cache;

import org.apache.http.annotation.ThreadSafe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implements a bounded failure cache. The oldest entries are discarded when
 * the maximum size is exceeded.
 */
@ThreadSafe
public class DefaultFailureCache implements FailureCache {

    static final int DEFAULT_MAX_SIZE = 1000;

    private final int maxSize;
    private final ConcurrentMap<String, FailureCacheValue> storage;

    /**
     * Create a new failure cache with the maximum size of
     * {@link #DEFAULT_MAX_SIZE}.
     */
    public DefaultFailureCache() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a new failure cache with the specified maximum size.
     * @param maxSize the maximum number of entries the cache should store
     */
    public DefaultFailureCache(int maxSize) {
        this.maxSize = maxSize;
        this.storage = new ConcurrentHashMap<String, FailureCacheValue>();
    }

    public int getErrorCount(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier may not be null");
        }
        FailureCacheValue storedErrorCode = storage.get(identifier);
        return storedErrorCode != null ? storedErrorCode.getErrorCount() : 0;
    }

    public void resetErrorCount(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier may not be null");
        }
        storage.remove(identifier);
    }

    public void increaseErrorCount(String identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier may not be null");
        }
        updateValue(identifier);
        removeOldestEntryIfMapSizeExceeded();
    }

    private void updateValue(String identifier) {
        /**
         * Due to concurrency it is possible that someone else is modifying an
         * entry before we could write back our updated value. So we keep
         * trying until it is our turn.
         */
        while (true) {
            FailureCacheValue oldValue = storage.get(identifier);
            if (oldValue == null) {
                FailureCacheValue newValue = new FailureCacheValue(identifier, 1);
                if (storage.putIfAbsent(identifier, newValue) == null) {
                    return;
                }
            }
            else {
                int errorCount = oldValue.getErrorCount();
                if (errorCount == Integer.MAX_VALUE) {
                    return;
                }
                FailureCacheValue newValue = new FailureCacheValue(identifier, errorCount + 1);
                if (storage.replace(identifier, oldValue, newValue)) {
                    return;
                }
            }
        }
    }

    private void removeOldestEntryIfMapSizeExceeded() {
        if (storage.size() > maxSize) {
            FailureCacheValue valueWithOldestTimestamp = findValueWithOldestTimestamp();
            if (valueWithOldestTimestamp != null) {
                storage.remove(valueWithOldestTimestamp.getKey(), valueWithOldestTimestamp);
            }
        }
    }

    private FailureCacheValue findValueWithOldestTimestamp() {
        long oldestTimestamp = Long.MAX_VALUE;
        FailureCacheValue oldestValue = null;
        for (Map.Entry<String, FailureCacheValue> storageEntry : storage.entrySet()) {
            FailureCacheValue value = storageEntry.getValue();
            long creationTimeInNanos = value.getCreationTimeInNanos();
            if (creationTimeInNanos < oldestTimestamp) {
                oldestTimestamp = creationTimeInNanos;
                oldestValue = storageEntry.getValue();
            }
        }
        return oldestValue;
    }
}
