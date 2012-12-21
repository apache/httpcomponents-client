package org.apache.http.impl.client.cache;

/**
 * Specifies when revalidation requests are scheduled.
 */
public interface SchedulingStrategy
{
    /**
     * Schedule an {@link AsynchronousValidationRequest} to be executed.
     *
     * @param revalidationRequest the request to be executed; not <code>null</code>
     * @throws java.util.concurrent.RejectedExecutionException if the request could not be scheduled for execution
     */
    void schedule(AsynchronousValidationRequest revalidationRequest);

    /**
     * Shutdown all claimed resources. It is up to the implementation how to
     * deal with delayed requests, but it is suggested to cancel them.
     */
    void shutdown();

}
