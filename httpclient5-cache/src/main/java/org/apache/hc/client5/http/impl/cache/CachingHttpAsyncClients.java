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

import java.io.File;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;

/**
 * Factory methods for {@link CloseableHttpAsyncClient} instances
 * capable of client-side caching.
 *
 * @since 5.0
 */
public final class CachingHttpAsyncClients {

    private CachingHttpAsyncClients() {
        super();
    }

    /**
     * Creates builder object for construction of custom
     * {@link CloseableHttpAsyncClient} instances.
     */
    public static CachingHttpAsyncClientBuilder custom() {
        return CachingHttpAsyncClientBuilder.create();
    }

    /**
     * Creates {@link CloseableHttpAsyncClient} instance that uses a memory bound
     * response cache.
     */
    public static CloseableHttpAsyncClient createMemoryBound() {
        return CachingHttpAsyncClientBuilder.create().build();
    }

    /**
     * Creates {@link CloseableHttpAsyncClient} instance that uses a file system
     * bound response cache.
     *
     * @param cacheDir location of response cache.
     */
    public static CloseableHttpAsyncClient createFileBound(final File cacheDir) {
        return CachingHttpAsyncClientBuilder.create().setCacheDir(cacheDir).build();
    }

    /**
     * Creates builder object for construction of custom HTTP/2
     * {@link CloseableHttpAsyncClient} instances.
     */
    public static CachingH2AsyncClientBuilder customHttp2() {
        return CachingH2AsyncClientBuilder.create();
    }

    /**
     * Creates HTTP/2 {@link CloseableHttpAsyncClient} instance that uses a memory bound
     * response cache.
     */
    public static CloseableHttpAsyncClient createHttp2MemoryBound() {
        return CachingH2AsyncClientBuilder.create().build();
    }

    /**
     * Creates HTTP/2 {@link CloseableHttpAsyncClient} instance that uses a file system
     * bound response cache.
     *
     * @param cacheDir location of response cache.
     */
    public static CloseableHttpAsyncClient createHttp2FileBound(final File cacheDir) {
        return CachingH2AsyncClientBuilder.create().setCacheDir(cacheDir).build();
    }

}
