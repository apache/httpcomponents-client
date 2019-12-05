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
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.hc.client5.http.cache.HttpCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.schedule.ImmediateSchedulingStrategy;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.http.config.NamedElementChain;

/**
 * Builder for {@link org.apache.hc.client5.http.impl.classic.CloseableHttpClient}
 * instances capable of client-side caching.
 *
 * @since 4.3
 */
public class CachingHttpClientBuilder extends HttpClientBuilder {

    private ResourceFactory resourceFactory;
    private HttpCacheStorage storage;
    private File cacheDir;
    private SchedulingStrategy schedulingStrategy;
    private CacheConfig cacheConfig;
    private HttpCacheInvalidator httpCacheInvalidator;
    private boolean deleteCache;

    public static CachingHttpClientBuilder create() {
        return new CachingHttpClientBuilder();
    }

    protected CachingHttpClientBuilder() {
        super();
        this.deleteCache = true;
    }

    public final CachingHttpClientBuilder setResourceFactory(
            final ResourceFactory resourceFactory) {
        this.resourceFactory = resourceFactory;
        return this;
    }

    public final CachingHttpClientBuilder setHttpCacheStorage(final HttpCacheStorage storage) {
        this.storage = storage;
        return this;
    }

    public final CachingHttpClientBuilder setCacheDir(final File cacheDir) {
        this.cacheDir = cacheDir;
        return this;
    }

    public final CachingHttpClientBuilder setSchedulingStrategy(final SchedulingStrategy schedulingStrategy) {
        this.schedulingStrategy = schedulingStrategy;
        return this;
    }

    public final CachingHttpClientBuilder setCacheConfig(final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
        return this;
    }

    public final CachingHttpClientBuilder setHttpCacheInvalidator(final HttpCacheInvalidator cacheInvalidator) {
        this.httpCacheInvalidator = cacheInvalidator;
        return this;
    }

    public final CachingHttpClientBuilder setDeleteCache(final boolean deleteCache) {
        this.deleteCache = deleteCache;
        return this;
    }

    @Override
    protected void customizeExecChain(final NamedElementChain<ExecChainHandler> execChainDefinition) {
        final CacheConfig config = this.cacheConfig != null ? this.cacheConfig : CacheConfig.DEFAULT;
        // We copy the instance fields to avoid changing them, and rename to avoid accidental use of the wrong version
        ResourceFactory resourceFactoryCopy = this.resourceFactory;
        if (resourceFactoryCopy == null) {
            if (this.cacheDir == null) {
                resourceFactoryCopy = new HeapResourceFactory();
            } else {
                resourceFactoryCopy = new FileResourceFactory(cacheDir);
            }
        }
        HttpCacheStorage storageCopy = this.storage;
        if (storageCopy == null) {
            if (this.cacheDir == null) {
                storageCopy = new BasicHttpCacheStorage(config);
            } else {
                final ManagedHttpCacheStorage managedStorage = new ManagedHttpCacheStorage(config);
                if (this.deleteCache) {
                    addCloseable(new Closeable() {

                        @Override
                        public void close() throws IOException {
                            managedStorage.shutdown();
                        }

                    });
                } else {
                    addCloseable(managedStorage);
                }
                storageCopy = managedStorage;
            }
        }
        final HttpCache httpCache = new BasicHttpCache(
                resourceFactoryCopy,
                storageCopy,
                CacheKeyGenerator.INSTANCE,
                this.httpCacheInvalidator != null ? this.httpCacheInvalidator : new DefaultCacheInvalidator());

        DefaultCacheRevalidator cacheRevalidator = null;
        if (config.getAsynchronousWorkers() > 0) {
            final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(config.getAsynchronousWorkers());
            addCloseable(new Closeable() {

                @Override
                public void close() throws IOException {
                    executorService.shutdownNow();
                }

            });
            cacheRevalidator = new DefaultCacheRevalidator(
                    executorService,
                    this.schedulingStrategy != null ? this.schedulingStrategy : ImmediateSchedulingStrategy.INSTANCE);
        }
        final CachingExec cachingExec = new CachingExec(
                httpCache,
                cacheRevalidator,
                config);
        execChainDefinition.addBefore(ChainElement.PROTOCOL.name(), cachingExec, ChainElement.CACHING.name());
    }

}
