package org.apache.http.client;

import org.apache.http.HttpResponse;

/**
 * When managing a dynamic number of connections for a given route, this
 * strategy assesses whether a given request execution outcome should
 * result in a backoff signal or not, based on either examining the
 * <code>Throwable</code> that resulted or by examining the resulting
 * response (e.g. for its status code).
 * 
 * @since 4.2
 *
 */
public interface ConnectionBackoffStrategy {

    /**
     * Determines whether seeing the given <code>Throwable</code> as
     * a result of request execution should result in a backoff
     * signal.
     * @param t the <code>Throwable</code> that happened
     * @return <code>true</code> if a backoff signal should be
     *   given
     */
    boolean shouldBackoff(Throwable t);

    /**
     * Determines whether receiving the given {@link HttpResponse} as
     * a result of request execution should result in a backoff
     * signal. Implementations MUST restrict themselves to examining
     * the response header and MUST NOT consume any of the response
     * body, if any.
     * @param t the <code>HttpResponse</code> that was received
     * @return <code>true</code> if a backoff signal should be
     *   given
     */
    boolean shouldBackoff(HttpResponse resp);
}
