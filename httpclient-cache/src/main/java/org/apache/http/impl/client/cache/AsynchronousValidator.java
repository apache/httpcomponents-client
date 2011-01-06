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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.protocol.HttpContext;

/**
 * Class used for asynchronous revalidations to be used when the "stale-
 * while-revalidate" directive is present
 */
class AsynchronousValidator {
    private final CachingHttpClient cachingClient;
    private final ExecutorService executor;
    private final Set<String> queued;
    private final CacheKeyGenerator cacheKeyGenerator;
    
    private final Log log = LogFactory.getLog(getClass());
    
    /**
     * Create AsynchronousValidator which will make revalidation requests
     * using the supplied {@link CachingHttpClient}, and 
     * a {@link ThreadPoolExecutor} generated according to the thread
     * pool settings provided in the given {@link CacheConfig}. 
     * @param cachingClient used to execute asynchronous requests
     * @param config specifies thread pool settings. See
     * {@link CacheConfig#getAsynchronousWorkersMax()},
     * {@link CacheConfig#getAsynchronousWorkersCore()},
     * {@link CacheConfig#getAsynchronousWorkerIdleLifetimeSecs()},
     * and {@link CacheConfig#getRevalidationQueueSize()}. 
     */
    public AsynchronousValidator(CachingHttpClient cachingClient,
            CacheConfig config) {
        this(cachingClient,
                new ThreadPoolExecutor(config.getAsynchronousWorkersCore(),
                        config.getAsynchronousWorkersMax(),
                        config.getAsynchronousWorkerIdleLifetimeSecs(),
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<Runnable>(config.getRevalidationQueueSize()))
                );
    }
    
    /**
     * Create AsynchronousValidator which will make revalidation requests
     * using the supplied {@link CachingHttpClient} and
     * {@link ExecutorService}.
     * @param cachingClient used to execute asynchronous requests
     * @param executor used to manage a thread pool of revalidation workers
     */
    AsynchronousValidator(CachingHttpClient cachingClient,
            ExecutorService executor) {
        this.cachingClient = cachingClient;
        this.executor = executor;
        this.queued = new HashSet<String>();
        this.cacheKeyGenerator = new CacheKeyGenerator();
    }
    
    /**
     * Schedules an asynchronous revalidation
     * 
     * @param target
     * @param request
     * @param context
     * @param entry
     */
    public synchronized void revalidateCacheEntry(HttpHost target,
            HttpRequest request, HttpContext context, HttpCacheEntry entry) {
        // getVariantURI will fall back on getURI if no variants exist
        String uri = cacheKeyGenerator.getVariantURI(target, request, entry);
        
        if (!queued.contains(uri)) {
            AsynchronousValidationRequest revalidationRequest =
                new AsynchronousValidationRequest(this, cachingClient, target,
                        request, context, entry, uri);

            try {
                executor.execute(revalidationRequest);
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
    
    Set<String> getScheduledIdentifiers() {
        return Collections.unmodifiableSet(queued);
    }
    
    ExecutorService getExecutor() {
        return executor;
    }
}
