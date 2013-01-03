package org.apache.http.impl.client.cache;

/**
 * Increase and reset the number of errors associated with a specific
 * identifier.
 */
public interface FailureCache {

    /**
     * Get the current error count.
     * @param identifier the identifier for which the error count is requested
     * @return the currently known error count or zero if there is no record
     */
    int getErrorCount(String identifier);

    /**
     * Reset the error count back to zero.
     * @param identifier the identifier for which the error count should be
     *                   reset
     */
    void resetErrorCount(String identifier);

    /**
     * Increases the error count by one.
     * @param identifier the identifier for which the error count should be
     *                   increased
     */
    void increaseErrorCount(String identifier);
}
