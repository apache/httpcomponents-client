package org.apache.http.impl.client;

/**
 * The actual system clock.
 * 
 * @since 4.2
 */
class SystemClock implements Clock {

    public long getCurrentTime() {
        return System.currentTimeMillis();
    }

}
