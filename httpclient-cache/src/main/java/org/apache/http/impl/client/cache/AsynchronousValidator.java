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
package org.apache.http.impl.client.cache;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.protocol.HttpContext;

/**
 * Class used for asynchronous revalidations to be used when the "stale-
 * while-revalidate" directive is present
 */
@ThreadSafe
class AsynchronousValidator {
    private final CachingHttpClient cachingClient;
    private final SchedulingStrategy schedulingStrategy;
    private final Set<String> queued;
    private final CacheKeyGenerator cacheKeyGenerator;
    private final FailureCache failureCache;

    private final Log log = LogFactory.getLog(getClass());

    /**
     * Create AsynchronousValidator which will make revalidation requests
     * using the supplied {@link CachingHttpClient}, and
     * execute them using a {@link ImmediateSchedulingStrategy}. Its thread
     * pool will be configured according to the given {@link CacheConfig}.
     * @param cachingClient used to execute asynchronous requests
     * @param config specifies thread pool settings. See
     * {@link CacheConfig#getAsynchronousWorkersMax()},
     * {@link CacheConfig#getAsynchronousWorkersCore()},
     * {@link CacheConfig#getAsynchronousWorkerIdleLifetimeSecs()},
     * and {@link CacheConfig#getRevalidationQueueSize()}.
     */
    public AsynchronousValidator(CachingHttpClient cachingClient,
            CacheConfig config) {
        this(cachingClient, new ImmediateSchedulingStrategy(config));
    }

    /**
     * Create AsynchronousValidator which will make revalidation requests
     * using the supplied {@link CachingHttpClient} and
     * {@link SchedulingStrategy}.
     * @param cachingClient used to execute asynchronous requests
     * @param schedulingStrategy used to maintain a pool of worker threads and
     *                           schedules when requests are executed
     */
    AsynchronousValidator(CachingHttpClient cachingClient,
                          SchedulingStrategy schedulingStrategy) {
        this.cachingClient = cachingClient;
        this.queued = new HashSet<String>();
        this.cacheKeyGenerator = new CacheKeyGenerator();
        this.schedulingStrategy = schedulingStrategy;
        this.failureCache = new DefaultFailureCache();
    }

    public synchronized void revalidateCacheEntry(HttpHost target,
            HttpRequest request, HttpContext context, HttpCacheEntry entry) {
        // getVariantURI will fall back on getURI if no variants exist
        String uri = cacheKeyGenerator.getVariantURI(target, request, entry);

        if (!queued.contains(uri)) {
            int consecutiveFailedAttempts = failureCache.getErrorCount(uri);
            AsynchronousValidationRequest revalidationRequest =
                new AsynchronousValidationRequest(this, cachingClient, target,
                        request, context, entry, uri, consecutiveFailedAttempts);

            try {
                schedulingStrategy.schedule(revalidationRequest);
                queued.add(uri);
            } catch (RejectedExecutionException ree) {
                log.debug("Revalidation for [" + uri + "] not scheduled: " + ree);
            }
        }
    }

    /**
     * Removes an identifier from the internal list of revalidation jobs in
     * progress.  This is meant to be called by
     * {@link AsynchronousValidationRequest#run()} once the revalidation is
     * complete, using the identifier passed in during constructions.
     * @param identifier
     */
    synchronized void markComplete(String identifier) {
        queued.remove(identifier);
    }

    /**
     * The revalidation job was successful thus the number of consecutive
     * failed attempts will be reset to zero. Should be called by
     * {@link AsynchronousValidationRequest#run()}.
     * @param identifier the revalidation job's unique identifier
     */
    void jobSuccessful(String identifier) {
        failureCache.resetErrorCount(identifier);
    }

    /**
     * The revalidation job did fail and thus the number of consecutive failed
     * attempts will be increased. Should be called by
     * {@link AsynchronousValidationRequest#run()}.
     * @param identifier the revalidation job's unique identifier
     */
    void jobFailed(String identifier) {
        failureCache.increaseErrorCount(identifier);
    }

    Set<String> getScheduledIdentifiers() {
        return Collections.unmodifiableSet(queued);
    }

    public void shutdown() {
        schedulingStrategy.shutdown();
    }
}
