package org.apache.http.impl.client.cache;

import org.apache.http.annotation.ThreadSafe;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Immediately schedules any incoming validation request. Relies on
 * {@link CacheConfig} to configure the used {@link ThreadPoolExecutor}.
 */
@ThreadSafe
public class ImmediateSchedulingStrategy implements SchedulingStrategy {

    private final ExecutorService executor;

    /**
     * Uses a {@link ThreadPoolExecutor} which is configured according to the
     * given {@link CacheConfig}.
     * @param cacheConfig specifies thread pool settings. See
     * {@link CacheConfig#getAsynchronousWorkersMax()},
     * {@link CacheConfig#getAsynchronousWorkersCore()},
     * {@link CacheConfig#getAsynchronousWorkerIdleLifetimeSecs()},
     * and {@link CacheConfig#getRevalidationQueueSize()}.
     */
    public ImmediateSchedulingStrategy(CacheConfig cacheConfig) {
        this(new ThreadPoolExecutor(
                cacheConfig.getAsynchronousWorkersCore(),
                cacheConfig.getAsynchronousWorkersMax(),
                cacheConfig.getAsynchronousWorkerIdleLifetimeSecs(),
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(cacheConfig.getRevalidationQueueSize()))
        );
    }

    ImmediateSchedulingStrategy(ExecutorService executor) {
        this.executor = executor;
    }

    public void schedule(AsynchronousValidationRequest revalidationRequest) {
        if (revalidationRequest == null) {
            throw new IllegalArgumentException("AsynchronousValidationRequest may not be null");
        }

        this.executor.execute(revalidationRequest);
    }

    public void shutdown() {
        executor.shutdown();
    }

    void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        executor.awaitTermination(timeout, unit);
    }
}
