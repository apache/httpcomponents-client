package org.apache.http.impl.client;

/**
 * Interface used to enable easier testing of time-related behavior.
 * 
 * @since 4.2
 *
 */
interface Clock {

    /**
     * Returns the current time, expressed as the number of
     * milliseconds since the epoch.
     * @return current time
     */
    long getCurrentTime();
}
