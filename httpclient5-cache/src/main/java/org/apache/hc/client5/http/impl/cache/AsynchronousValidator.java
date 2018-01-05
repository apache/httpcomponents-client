/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.hc.client5.http.impl.cache;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.impl.schedule.ImmediateSchedulingStrategy;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class used for asynchronous revalidations to be used when the "stale-
 * while-revalidate" directive is present
 */
class AsynchronousValidator implements Closeable {

    private final ScheduledExecutorService executorService;
    private final SchedulingStrategy schedulingStrategy;
    private final Set<String> queued;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final FailureCache failureCache;

    private final Logger log = LogManager.getLogger(getClass());

    /**
     * Create AsynchronousValidator which will make revalidation requests
     * using an {@link ImmediateSchedulingStrategy}. Its thread
     * pool will be configured according to the given {@link CacheConfig}.
     * @param config specifies thread pool settings. See
     * {@link CacheConfig#getAsynchronousWorkersMax()},
     * {@link CacheConfig#getAsynchronousWorkersCore()},
     * {@link CacheConfig#getAsynchronousWorkerIdleLifetimeSecs()},
     * and {@link CacheConfig#getRevalidationQueueSize()}.
     */
    public AsynchronousValidator(final CacheConfig config) {
        this(new ScheduledThreadPoolExecutor(config.getAsynchronousWorkersCore()), new ImmediateSchedulingStrategy());
    }

    /**
     * Create AsynchronousValidator which will make revalidation requests
     * using the supplied {@link SchedulingStrategy}. Closing the validator
     * will also close the given schedulingStrategy.
     * @param schedulingStrategy used to maintain a pool of worker threads and
     *                           schedules when requests are executed
     */
    AsynchronousValidator(final ScheduledExecutorService executorService, final SchedulingStrategy schedulingStrategy) {
        this.executorService = executorService;
        this.schedulingStrategy = schedulingStrategy;
        this.queued = new HashSet<>();
        this.cacheKeyGenerator = CacheKeyGenerator.INSTANCE;
        this.failureCache = new DefaultFailureCache();
    }

    /**
     * Schedules an asynchronous revalidation
     */
    public synchronized void revalidateCacheEntry(
            final CachingExec cachingExec,
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain,
            final HttpCacheEntry entry) {
        // getVariantURI will fall back on getURI if no variants exist
        final String cacheKey = cacheKeyGenerator.generateVariantURI(target, request, entry);

        if (!queued.contains(cacheKey)) {
            final int consecutiveFailedAttempts = failureCache.getErrorCount(cacheKey);
            final AsynchronousValidationRequest revalidationRequest =
                new AsynchronousValidationRequest(
                        this, cachingExec, target, request, scope, chain, entry, cacheKey, consecutiveFailedAttempts);

            final TimeValue executionTime = schedulingStrategy.schedule(consecutiveFailedAttempts);
            try {
                executorService.schedule(revalidationRequest, executionTime.getDuration(), executionTime.getTimeUnit());
                queued.add(cacheKey);
            } catch (final RejectedExecutionException ree) {
                log.debug("Revalidation for [" + cacheKey + "] not scheduled: " + ree);
            }
        }
    }

    @Override
    public void close() throws IOException {
        executorService.shutdown();
    }

    public void awaitTermination(final Timeout timeout) throws InterruptedException {
        Args.notNull(timeout, "Timeout");
        executorService.awaitTermination(timeout.getDuration(), timeout.getTimeUnit());
    }

    /**
     * Removes an identifier from the internal list of revalidation jobs in
     * progress.  This is meant to be called by
     * {@link AsynchronousValidationRequest#run()} once the revalidation is
     * complete, using the identifier passed in during constructions.
     * @param identifier
     */
    synchronized void markComplete(final String identifier) {
        queued.remove(identifier);
    }

    /**
     * The revalidation job was successful thus the number of consecutive
     * failed attempts will be reset to zero. Should be called by
     * {@link AsynchronousValidationRequest#run()}.
     * @param identifier the revalidation job's unique identifier
     */
    void jobSuccessful(final String identifier) {
        failureCache.resetErrorCount(identifier);
    }

    /**
     * The revalidation job did fail and thus the number of consecutive failed
     * attempts will be increased. Should be called by
     * {@link AsynchronousValidationRequest#run()}.
     * @param identifier the revalidation job's unique identifier
     */
    void jobFailed(final String identifier) {
        failureCache.increaseErrorCount(identifier);
    }

    Set<String> getScheduledIdentifiers() {
        return Collections.unmodifiableSet(queued);
    }
}
