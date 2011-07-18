package org.apache.http.impl.client;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ConnectionBackoffStrategy;

/**
 * This {@link ConnectionBackoffStrategy} backs off either for a raw
 * network socket or connection timeout or if the server explicitly
 * sends a 503 (Service Unavailable) response.
 * 
 * @since 4.2
 */
public class DefaultBackoffStrategy implements ConnectionBackoffStrategy {

    public boolean shouldBackoff(Throwable t) {
        return (t instanceof SocketTimeoutException
                || t instanceof ConnectException);
    }

    public boolean shouldBackoff(HttpResponse resp) {
        return (resp.getStatusLine().getStatusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE);
    }

}
