package org.apache.http.impl.client;

import org.apache.http.HttpResponse;
import org.apache.http.client.ConnectionBackoffStrategy;

/**
 * This is a {@link ConnectionBackoffStrategy} that never backs off,
 * for compatibility with existing behavior.
 * 
 * @since 4.2
 */
public class NullBackoffStrategy implements ConnectionBackoffStrategy {

    public boolean shouldBackoff(Throwable t) {
        return false;
    }

    public boolean shouldBackoff(HttpResponse resp) {
        return false;
    }
}
