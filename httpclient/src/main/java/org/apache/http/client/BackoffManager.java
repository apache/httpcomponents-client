package org.apache.http.client;

import org.apache.http.conn.routing.HttpRoute;

/**
 * Represents a controller that dynamically adjusts the size
 * of an available connection pool based on feedback from
 * using the connections.
 * 
 * @since 4.2
 *
 */
public interface BackoffManager {

    /**
     * Called when we have decided that the result of
     * using a connection should be interpreted as a
     * backoff signal.
     */
    public void backOff(HttpRoute route);
    
    /**
     * Called when we have determined that the result of
     * using a connection has succeeded and that we may
     * probe for more connections.
     */
    public void probe(HttpRoute route);
}
