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
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;

/**
 * Class used for asynchronous revalidations to be used when
 * the {@code stale-while-revalidate} directive is present
 */
class DefaultCacheRevalidator extends CacheRevalidatorBase {

    private final CachingExec cachingExec;
    private final CacheKeyGenerator cacheKeyGenerator;

    /**
     * Create DefaultCacheRevalidator which will make ache revalidation requests
     * using the supplied {@link SchedulingStrategy} and {@link ScheduledExecutor}.
     */
    public DefaultCacheRevalidator(
            final CacheRevalidatorBase.ScheduledExecutor scheduledExecutor,
            final SchedulingStrategy schedulingStrategy,
            final CachingExec cachingExec) {
        super(scheduledExecutor, schedulingStrategy);
        this.cachingExec = cachingExec;
        this.cacheKeyGenerator = CacheKeyGenerator.INSTANCE;

    }

    /**
     * Create CacheValidator which will make ache revalidation requests
     * using the supplied {@link SchedulingStrategy} and {@link ScheduledThreadPoolExecutor}.
     */
    public DefaultCacheRevalidator(
            final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor,
            final SchedulingStrategy schedulingStrategy,
            final CachingExec cachingExec) {
        this(wrap(scheduledThreadPoolExecutor), schedulingStrategy, cachingExec);
    }

    /**
     * Schedules an asynchronous re-validation
     */
    public void revalidateCacheEntry(
            final HttpHost target,
            final ClassicHttpRequest request,
            final ExecChain.Scope scope,
            final ExecChain chain,
            final HttpCacheEntry entry) {
        final String cacheKey = cacheKeyGenerator.generateKey(target, request, entry);
        scheduleRevalidation(cacheKey, new Runnable() {

                        @Override
                        public void run() {
                            try {
                                try (ClassicHttpResponse httpResponse = cachingExec.revalidateCacheEntry(target, request, scope, chain, entry)) {
                                    if (httpResponse.getCode() < HttpStatus.SC_SERVER_ERROR && !isStale(httpResponse)) {
                                        jobSuccessful(cacheKey);
                                    } else {
                                        jobFailed(cacheKey);
                                    }
                                }
                            } catch (final IOException ioe) {
                                jobFailed(cacheKey);
                                log.debug("Asynchronous revalidation failed due to I/O error", ioe);
                            } catch (final HttpException pe) {
                                jobFailed(cacheKey);
                                log.error("HTTP protocol exception during asynchronous revalidation", pe);
                            } catch (final RuntimeException re) {
                                jobFailed(cacheKey);
                                log.error("Unexpected runtime exception thrown during asynchronous revalidation" + re);
                            }

                        }

                    });
    }

}
