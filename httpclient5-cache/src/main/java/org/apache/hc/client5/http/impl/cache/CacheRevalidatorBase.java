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
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.hc.client5.http.schedule.ConcurrentCountMap;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.util.Args;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract cache re-validation class.
 */
class CacheRevalidatorBase implements Closeable {

    private final ReentrantLock lock;

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

    private static final Logger LOG = LoggerFactory.getLogger(CacheRevalidatorBase.class);

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
        this.lock = new ReentrantLock();
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
        lock.lock();
        try {
            if (!pendingRequest.contains(cacheKey)) {
                final int consecutiveFailedAttempts = failureCache.getCount(cacheKey);
                final TimeValue executionTime = schedulingStrategy.schedule(consecutiveFailedAttempts);
                try {
                    scheduledExecutor.schedule(command, executionTime);
                    pendingRequest.add(cacheKey);
                } catch (final RejectedExecutionException ex) {
                    LOG.debug("Revalidation of cache entry with key {} could not be scheduled", cacheKey, ex);
                }
            }
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            pendingRequest.remove(identifier);
        } finally {
            lock.unlock();
        }
    }

    void jobFailed(final String identifier) {
        failureCache.increaseCount(identifier);
        lock.lock();
        try {
            pendingRequest.remove(identifier);
        } finally {
            lock.unlock();
        }
    }

    Set<String> getScheduledIdentifiers() {
        lock.lock();
        try {
            return new HashSet<>(pendingRequest);
        } finally {
            lock.unlock();
        }
    }

}
