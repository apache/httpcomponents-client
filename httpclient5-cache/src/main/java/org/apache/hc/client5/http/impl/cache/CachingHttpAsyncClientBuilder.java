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

import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.apache.hc.client5.http.cache.HttpAsyncCacheInvalidator;
import org.apache.hc.client5.http.cache.HttpAsyncCacheStorage;
import org.apache.hc.client5.http.cache.HttpAsyncCacheStorageAdaptor;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceFactory;
import org.apache.hc.client5.http.impl.ChainElement;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.schedule.ImmediateSchedulingStrategy;
import org.apache.hc.client5.http.schedule.SchedulingStrategy;
import org.apache.hc.core5.annotation.Experimental;
import org.apache.hc.core5.http.config.NamedElementChain;

/**
 * Builder for {@link org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient}
 * instances capable of client-side caching.
 *
 * @since 5.0
 */
@Experimental
public class CachingHttpAsyncClientBuilder extends HttpAsyncClientBuilder {

    private ResourceFactory resourceFactory;
    private HttpAsyncCacheStorage storage;
    private File cacheDir;
    private SchedulingStrategy schedulingStrategy;
    private CacheConfig cacheConfig;
    private HttpAsyncCacheInvalidator httpCacheInvalidator;
    private boolean deleteCache;

    public static CachingHttpAsyncClientBuilder create() {
        return new CachingHttpAsyncClientBuilder();
    }

    protected CachingHttpAsyncClientBuilder() {
        super();
        this.deleteCache = true;
    }

    public final CachingHttpAsyncClientBuilder setResourceFactory(final ResourceFactory resourceFactory) {
        this.resourceFactory = resourceFactory;
        return this;
    }

    public final CachingHttpAsyncClientBuilder setHttpCacheStorage(final HttpCacheStorage storage) {
        this.storage = storage != null ? new HttpAsyncCacheStorageAdaptor(storage) : null;
        return this;
    }

    public final CachingHttpAsyncClientBuilder setHttpCacheStorage(final HttpAsyncCacheStorage storage) {
        this.storage = storage;
        return this;
    }

    public final CachingHttpAsyncClientBuilder setCacheDir(final File cacheDir) {
        this.cacheDir = cacheDir;
        return this;
    }

    public final CachingHttpAsyncClientBuilder setSchedulingStrategy(final SchedulingStrategy schedulingStrategy) {
        this.schedulingStrategy = schedulingStrategy;
        return this;
    }

    public final CachingHttpAsyncClientBuilder setCacheConfig(final CacheConfig cacheConfig) {
        this.cacheConfig = cacheConfig;
        return this;
    }

    public final CachingHttpAsyncClientBuilder setHttpCacheInvalidator(final HttpAsyncCacheInvalidator cacheInvalidator) {
        this.httpCacheInvalidator = cacheInvalidator;
        return this;
    }

    public CachingHttpAsyncClientBuilder setDeleteCache(final boolean deleteCache) {
        this.deleteCache = deleteCache;
        return this;
    }

    @Override
    protected void customizeExecChain(final NamedElementChain<AsyncExecChainHandler> execChainDefinition) {
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
        HttpAsyncCacheStorage storageCopy = this.storage;
        if (storageCopy == null) {
            if (this.cacheDir == null) {
                storageCopy = new HttpAsyncCacheStorageAdaptor(new BasicHttpCacheStorage(config));
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
                storageCopy = new HttpAsyncCacheStorageAdaptor(managedStorage);
            }
        }
        final HttpAsyncCache httpCache = new BasicHttpAsyncCache(
                resourceFactoryCopy,
                storageCopy,
                CacheKeyGenerator.INSTANCE,
                this.httpCacheInvalidator != null ? this.httpCacheInvalidator : new DefaultAsyncCacheInvalidator());

        DefaultAsyncCacheRevalidator cacheRevalidator = null;
        if (config.getAsynchronousWorkers() > 0) {
            final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(config.getAsynchronousWorkers());
            addCloseable(new Closeable() {

                @Override
                public void close() throws IOException {
                    executorService.shutdownNow();
                }

            });
            cacheRevalidator = new DefaultAsyncCacheRevalidator(
                    executorService,
                    this.schedulingStrategy != null ? this.schedulingStrategy : ImmediateSchedulingStrategy.INSTANCE);
        }

        final AsyncCachingExec cachingExec = new AsyncCachingExec(
                httpCache,
                cacheRevalidator,
                config);
        execChainDefinition.addBefore(ChainElement.PROTOCOL.name(), cachingExec, ChainElement.CACHING.name());
    }

}
