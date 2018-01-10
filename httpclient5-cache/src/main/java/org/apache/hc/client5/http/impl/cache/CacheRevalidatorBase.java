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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.hc.client5.http.cache.HeaderConstants;
import org.apache.hc.client5.http.schedule.ConcurrentCountMap;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract cache re-validation class.
 */
class CacheRevalidatorBase implements Closeable {

    interface ScheduledExecutor {

        Future<?> schedule(Runnable command, TimeValue timeValue) throws RejectedExecutionException;

        void shutdown();

        void awaitTermination(final Timeout timeout) throws InterruptedException;

    }

    public static ScheduledExecutor wrap(final ScheduledExecutorService executorService) {

        return new ScheduledExecutor() {

            @Override
            public ScheduledFuture<?> schedule(final Runnable command, final TimeValue timeValue) throws RejectedExecutionException {
                Args.notNull(command, "Runnable");
                Args.notNull(timeValue, "Time value");
                return executorService.schedule(command, timeValue.getDuration(), timeValue.getTimeUnit());
            }

            @Override
            public void shutdown() {
                executorService.shutdown();
            }

            @Override
            public void awaitTermination(final Timeout timeout) throws InterruptedException {
                Args.notNull(timeout, "Timeout");
                executorService.awaitTermination(timeout.getDuration(), timeout.getTimeUnit());
            }

        };

    }

    private final ScheduledExecutor scheduledExecutor;
    private final SchedulingStrategy schedulingStrategy;
    private final Set<String> pendingRequest;
    private final ConcurrentCountMap<String> failureCache;

    final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Create CacheValidator which will make ache revalidation requests
     * using the supplied {@link SchedulingStrategy} and {@link ScheduledExecutor}.
     */
    public CacheRevalidatorBase(
            final ScheduledExecutor scheduledExecutor,
            final SchedulingStrategy schedulingStrategy) {
        this.scheduledExecutor = scheduledExecutor;
        this.schedulingStrategy = schedulingStrategy;
        this.pendingRequest = new HashSet<>();
        this.failureCache = new ConcurrentCountMap<>();
    }

    /**
     * Create CacheValidator which will make ache revalidation requests
     * using the supplied {@link SchedulingStrategy} and {@link ScheduledThreadPoolExecutor}.
     */
    public CacheRevalidatorBase(
            final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor,
            final SchedulingStrategy schedulingStrategy) {
        this(wrap(scheduledThreadPoolExecutor), schedulingStrategy);
    }

    /**
     * Schedules an asynchronous re-validation
     */
    void scheduleRevalidation(final String cacheKey, final Runnable command) {
        synchronized (pendingRequest) {
            if (!pendingRequest.contains(cacheKey)) {
                final int consecutiveFailedAttempts = failureCache.getCount(cacheKey);
                final TimeValue executionTime = schedulingStrategy.schedule(consecutiveFailedAttempts);
                try {
                    scheduledExecutor.schedule(command, executionTime);
                    pendingRequest.add(cacheKey);
                } catch (final RejectedExecutionException ex) {
                    log.debug("Revalidation of cache entry with key " + cacheKey + "could not be scheduled: " + ex);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        scheduledExecutor.shutdown();
    }

    public void awaitTermination(final Timeout timeout) throws InterruptedException {
        Args.notNull(timeout, "Timeout");
        scheduledExecutor.awaitTermination(timeout);
    }

    void jobSuccessful(final String identifier) {
        failureCache.resetCount(identifier);
        synchronized (pendingRequest) {
            pendingRequest.remove(identifier);
        }
    }

    void jobFailed(final String identifier) {
        failureCache.increaseCount(identifier);
        synchronized (pendingRequest) {
            pendingRequest.remove(identifier);
        }
    }

    Set<String> getScheduledIdentifiers() {
        synchronized (pendingRequest) {
            return new HashSet<>(pendingRequest);
        }
    }

    /**
     * Determines if the given response is generated from a stale cache entry.
     * @param httpResponse the response to be checked
     * @return whether the response is stale or not
     */
    boolean isStale(final HttpResponse httpResponse) {
        for (final Iterator<Header> it = httpResponse.headerIterator(HeaderConstants.WARNING); it.hasNext(); ) {
            /*
             * warn-codes
             * 110 = Response is stale
             * 111 = Revalidation failed
             */
            final Header warning = it.next();
            final String warningValue = warning.getValue();
            if (warningValue.startsWith("110") || warningValue.startsWith("111")) {
                return true;
            }
        }
        return false;
    }

}
