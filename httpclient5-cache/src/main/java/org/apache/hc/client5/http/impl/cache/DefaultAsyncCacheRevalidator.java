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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Class used for asynchronous revalidations to be used when the {@code stale-while-revalidate}
 * directive is present
 */
class DefaultAsyncCacheRevalidator extends CacheRevalidatorBase {

    private static final Future<Void> NOOP_FUTURE = new Future<Void>() {

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            return null;
        }

        @Override
        public Void get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return null;
        }
        @Override
        public boolean cancel(final boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

    };

    static class InternalScheduledExecutor implements ScheduledExecutor {

        private final ScheduledExecutor executor;

        InternalScheduledExecutor(final ScheduledExecutor executor) {
            this.executor = executor;
        }

        @Override
        public Future<?> schedule(final Runnable command, final TimeValue timeValue) throws RejectedExecutionException {
            if (timeValue.toMillis() <= 0) {
                command.run();
                return NOOP_FUTURE;
            } else {
                return executor.schedule(command, timeValue);
            }
        }

        @Override
        public void shutdown() {
            executor.shutdown();
        }

        @Override
        public void awaitTermination(final Timeout timeout) throws InterruptedException {
            executor.awaitTermination(timeout);
        }

    }

    private final AsyncCachingExec cachingExec;
    private final CacheKeyGenerator cacheKeyGenerator;

    /**
     * Create DefaultCacheRevalidator which will make ache revalidation requests
     * using the supplied {@link SchedulingStrategy} and {@link ScheduledExecutor}.
     */
    public DefaultAsyncCacheRevalidator(
            final ScheduledExecutor scheduledExecutor,
            final SchedulingStrategy schedulingStrategy,
            final AsyncCachingExec cachingExec) {
        super(new InternalScheduledExecutor(scheduledExecutor), schedulingStrategy);
        this.cachingExec = cachingExec;
        this.cacheKeyGenerator = CacheKeyGenerator.INSTANCE;

    }

    /**
     * Create CacheValidator which will make ache revalidation requests
     * using the supplied {@link SchedulingStrategy} and {@link ScheduledThreadPoolExecutor}.
     */
    public DefaultAsyncCacheRevalidator(
            final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor,
            final SchedulingStrategy schedulingStrategy,
            final AsyncCachingExec cachingExec) {
        this(wrap(scheduledThreadPoolExecutor), schedulingStrategy, cachingExec);
    }

    /**
     * Schedules an asynchronous re-validation
     */
    public void revalidateCacheEntry(
            final HttpHost target,
            final HttpRequest request,
            final AsyncEntityProducer entityProducer,
            final AsyncExecChain.Scope scope,
            final AsyncExecChain chain,
            final AsyncExecCallback asyncExecCallback,
            final HttpCacheEntry entry) {
        final String cacheKey = cacheKeyGenerator.generateKey(target, request, entry);
        scheduleRevalidation(cacheKey, new Runnable() {

                        @Override
                        public void run() {
                            cachingExec.revalidateCacheEntry(target, request, entityProducer, scope, chain, asyncExecCallback, entry);
                        }

                    });
    }

}
