package org.apache.http.impl.client.cache;

import org.apache.http.annotation.ThreadSafe;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * An implementation that backs off exponentially based on the number of
 * consecutive failed attempts stored in the
 * {@link AsynchronousValidationRequest}. It uses the following defaults:
 * <pre>
 *         no delay in case it was never tried or didn't fail so far
 *     6 secs delay for one failed attempt (= {@link #getInitialExpiryInMillis()})
 *    60 secs delay for two failed attempts
 *    10 mins delay for three failed attempts
 *   100 mins delay for four failed attempts
 *  ~16 hours delay for five failed attempts
 *   24 hours delay for six or more failed attempts (= {@link #getMaxExpiryInMillis()})
 * </pre>
 *
 * The following equation is used to calculate the delay for a specific revalidation request:
 * <pre>
 *     delay = {@link #getInitialExpiryInMillis()} * Math.pow({@link #getBackOffRate()}, {@link AsynchronousValidationRequest#getConsecutiveFailedAttempts()} - 1))
 * </pre>
 * The resulting delay won't exceed {@link #getMaxExpiryInMillis()}.
 */
@ThreadSafe
public class ExponentialBackingOffSchedulingStrategy implements SchedulingStrategy {

    public static final long DEFAULT_BACK_OFF_RATE = 10;
    public static final long DEFAULT_INITIAL_EXPIRY_IN_MILLIS = TimeUnit.SECONDS.toMillis(6);
    public static final long DEFAULT_MAX_EXPIRY_IN_MILLIS = TimeUnit.DAYS.toMillis(1);

    private final long backOffRate;
    private final long initialExpiryInMillis;
    private final long maxExpiryInMillis;

    private final ScheduledExecutorService executor;

    /**
     * Create a new scheduling strategy using a fixed pool of worker threads.
     * @param cacheConfig the thread pool configuration to be used; not <code>null</code>
     * @see org.apache.http.impl.client.cache.CacheConfig#getAsynchronousWorkersMax()
     * @see #DEFAULT_BACK_OFF_RATE
     * @see #DEFAULT_INITIAL_EXPIRY_IN_MILLIS
     * @see #DEFAULT_MAX_EXPIRY_IN_MILLIS
     */
    public ExponentialBackingOffSchedulingStrategy(CacheConfig cacheConfig) {
        this(cacheConfig, DEFAULT_BACK_OFF_RATE, DEFAULT_INITIAL_EXPIRY_IN_MILLIS, DEFAULT_MAX_EXPIRY_IN_MILLIS);
    }

    /**
     * Create a new scheduling strategy by using a fixed pool of worker threads and the given parameters to calculated
     * the delay.
     *
     * @param cacheConfig the thread pool configuration to be used; not <code>null</code>
     * @param backOffRate the back off rate to be used; not negative
     * @param initialExpiryInMillis the initial expiry in milli seconds; not negative
     * @param maxExpiryInMillis the upper limit of the delay in milli seconds; not negative
     * @see org.apache.http.impl.client.cache.CacheConfig#getAsynchronousWorkersMax()
     * @see ExponentialBackingOffSchedulingStrategy
     */
    public ExponentialBackingOffSchedulingStrategy(CacheConfig cacheConfig, long backOffRate, long initialExpiryInMillis, long maxExpiryInMillis) {
        this(createThreadPoolFromCacheConfig(cacheConfig), backOffRate, initialExpiryInMillis, maxExpiryInMillis);
    }

    private static ScheduledThreadPoolExecutor createThreadPoolFromCacheConfig(CacheConfig cacheConfig) {
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(cacheConfig.getAsynchronousWorkersMax());
        scheduledThreadPoolExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduledThreadPoolExecutor;
    }

    ExponentialBackingOffSchedulingStrategy(ScheduledExecutorService executor, long backOffRate, long initialExpiryInMillis, long maxExpiryInMillis) {
        this.executor = checkNotNull("executor", executor);
        this.backOffRate = checkNotNegative("backOffRate", backOffRate);
        this.initialExpiryInMillis = checkNotNegative("initialExpiryInMillis", initialExpiryInMillis);
        this.maxExpiryInMillis = checkNotNegative("maxExpiryInMillis", maxExpiryInMillis);
    }

    public void schedule(AsynchronousValidationRequest revalidationRequest) {
        checkNotNull("revalidationRequest", revalidationRequest);
        int consecutiveFailedAttempts = revalidationRequest.getConsecutiveFailedAttempts();
        long delayInMillis = calculateDelayInMillis(consecutiveFailedAttempts);
        executor.schedule(revalidationRequest, delayInMillis, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        executor.shutdown();
    }

    public long getBackOffRate() {
        return backOffRate;
    }

    public long getInitialExpiryInMillis() {
        return initialExpiryInMillis;
    }

    public long getMaxExpiryInMillis() {
        return maxExpiryInMillis;
    }

    protected long calculateDelayInMillis(int consecutiveFailedAttempts) {
        if (consecutiveFailedAttempts > 0) {
            long delayInSeconds = (long) (initialExpiryInMillis * Math.pow(backOffRate, consecutiveFailedAttempts - 1));
            return Math.min(delayInSeconds, maxExpiryInMillis);
        }
        else {
            return 0;
        }
    }

    private static <T> T checkNotNull(String parameterName, T value) {
        if (value == null) {
            throw new IllegalArgumentException(parameterName + " may not be null");
        }
        return value;
    }

    private static long checkNotNegative(String parameterName, long value) {
        if (value < 0) {
            throw new IllegalArgumentException(parameterName + " may not be negative");
        }
        return value;
    }
}
