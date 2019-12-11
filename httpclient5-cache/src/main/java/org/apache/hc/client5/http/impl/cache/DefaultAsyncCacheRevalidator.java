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

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.impl.Operations;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Class used for asynchronous revalidations to be used when the {@code stale-while-revalidate}
 * directive is present
 */
class DefaultAsyncCacheRevalidator extends CacheRevalidatorBase {

    interface RevalidationCall {

        void execute(AsyncExecCallback asyncExecCallback);
    }

    static class InternalScheduledExecutor implements ScheduledExecutor {

        private final ScheduledExecutor executor;

        InternalScheduledExecutor(final ScheduledExecutor executor) {
            this.executor = executor;
        }

        @Override
        public Future<?> schedule(final Runnable command, final TimeValue timeValue) throws RejectedExecutionException {
            if (timeValue.toMilliseconds() <= 0) {
                command.run();
                return new Operations.CompletedFuture<Void>(null);
            }
            return executor.schedule(command, timeValue);
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

    private final CacheKeyGenerator cacheKeyGenerator;

    /**
     * Create DefaultCacheRevalidator which will make ache revalidation requests
     * using the supplied {@link SchedulingStrategy} and {@link ScheduledExecutor}.
     */
    public DefaultAsyncCacheRevalidator(
            final ScheduledExecutor scheduledExecutor,
            final SchedulingStrategy schedulingStrategy) {
        super(new InternalScheduledExecutor(scheduledExecutor), schedulingStrategy);
        this.cacheKeyGenerator = CacheKeyGenerator.INSTANCE;

    }

    /**
     * Create CacheValidator which will make ache revalidation requests
     * using the supplied {@link SchedulingStrategy} and {@link ScheduledExecutorService}.
     */
    public DefaultAsyncCacheRevalidator(
            final ScheduledExecutorService executorService,
            final SchedulingStrategy schedulingStrategy) {
        this(wrap(executorService), schedulingStrategy);
    }

    /**
     * Schedules an asynchronous re-validation
     */
    public void revalidateCacheEntry(
            final String cacheKey ,
            final AsyncExecCallback asyncExecCallback,
            final RevalidationCall call) {
        scheduleRevalidation(cacheKey, new Runnable() {

                        @Override
                        public void run() {
                            call.execute(new AsyncExecCallback() {

                                private final AtomicReference<HttpResponse> responseRef = new AtomicReference<>(null);

                                @Override
                                public AsyncDataConsumer handleResponse(
                                        final HttpResponse response,
                                        final EntityDetails entityDetails) throws HttpException, IOException {
                                    responseRef.set(response);
                                    return asyncExecCallback.handleResponse(response, entityDetails);
                                }

                                @Override
                                public void handleInformationResponse(
                                        final HttpResponse response) throws HttpException, IOException {
                                    asyncExecCallback.handleInformationResponse(response);
                                }

                                @Override
                                public void completed() {
                                    final HttpResponse httpResponse = responseRef.getAndSet(null);
                                    if (httpResponse != null && httpResponse.getCode() < HttpStatus.SC_SERVER_ERROR && !isStale(httpResponse)) {
                                        jobSuccessful(cacheKey);
                                    } else {
                                        jobFailed(cacheKey);
                                    }
                                    asyncExecCallback.completed();
                                }

                                @Override
                                public void failed(final Exception cause) {
                                    if (cause instanceof IOException) {
                                        log.debug("Asynchronous revalidation failed due to I/O error", cause);
                                    } else if (cause instanceof HttpException) {
                                        log.error("HTTP protocol exception during asynchronous revalidation", cause);
                                    } else {
                                        log.error("Unexpected runtime exception thrown during asynchronous revalidation", cause);
                                    }
                                    try {
                                        jobFailed(cacheKey);
                                    } finally {
                                        asyncExecCallback.failed(cause);
                                    }
                                }

                            });
                        }

                    });
    }

}
