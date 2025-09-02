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

package org.apache.hc.client5.http.impl.classic;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;

/**
 * Factory methods for {@link CloseableHttpClient} instances.
 *
 * @since 4.3
 */
public final class HttpClients {

    private HttpClients() {
        super();
    }

    /**
     * Creates builder object for construction of custom
     * {@link CloseableHttpClient} instances.
     */
    public static HttpClientBuilder custom() {
        return HttpClientBuilder.create();
    }

    /**
     * Creates {@link CloseableHttpClient} instance with default
     * configuration.
     */
    public static CloseableHttpClient createDefault() {
        return HttpClientBuilder.create().build();
    }

    /**
     * Creates {@link CloseableHttpClient} instance with default
     * configuration based on system properties.
     */
    public static CloseableHttpClient createSystem() {
        return HttpClientBuilder.create().useSystemProperties().build();
    }

    /**
     * Creates {@link CloseableHttpClient} instance that implements
     * the most basic HTTP protocol support.
     */
    public static MinimalHttpClient createMinimal() {
        return new MinimalHttpClient(new PoolingHttpClientConnectionManager());
    }

    /**
     * Creates {@link CloseableHttpClient} instance that implements
     * the most basic HTTP protocol support.
     */
    public static MinimalHttpClient createMinimal(final HttpClientConnectionManager connManager) {
        return new MinimalHttpClient(connManager);
    }

    /**
     * Creates a client with default configuration executing transport on virtual threads (JDK 21+).
     * <p>Response handlers run on the caller thread. If virtual threads are unavailable at runtime,
     * this method falls back to classic execution (same as {@link #createDefault()}).</p>
     * @since 5.6
     */
    public static CloseableHttpClient createVirtualThreadDefault() {
        return HttpClientBuilder.create()
                .useVirtualThreads()
                .build();
    }

    /**
     * Same as {@link #createVirtualThreadDefault()} but honors system properties.
     * <p>If virtual threads are unavailable at runtime, falls back to classic execution.</p>
     * @since 5.6
     */
    public static CloseableHttpClient createVirtualThreadSystem() {
        return HttpClientBuilder.create()
                .useSystemProperties()
                .useVirtualThreads()
                .build();
    }

    /**
     * Returns a builder preconfigured to execute transport on virtual threads (JDK 21+).
     * <p>If virtual threads are unavailable at runtime, the built client falls back to classic execution.</p>
     * @since 5.6
     */
    public static HttpClientBuilder customVirtualThreads() {
        return HttpClientBuilder.create()
                .useVirtualThreads();
    }

    /**
     * Returns a builder preconfigured to execute transport on virtual threads with a custom thread name prefix.
     * <p>If virtual threads are unavailable at runtime, the built client falls back to classic execution and the
     * prefix is ignored.</p>
     * @since 5.6
     */
    public static HttpClientBuilder customVirtualThreads(final String namePrefix) {
        return HttpClientBuilder.create()
                .useVirtualThreads()
                .virtualThreadNamePrefix(namePrefix);
    }

    /**
     * Creates a virtual-thread client with a custom thread name prefix.
     * <p>If virtual threads are unavailable at runtime, falls back to classic execution and the prefix is ignored.</p>
     * @since 5.6
     */
    public static CloseableHttpClient createVirtualThreadDefault(final String namePrefix) {
        return HttpClientBuilder.create()
                .useVirtualThreads()
                .virtualThreadNamePrefix(namePrefix)
                .build();
    }

    /**
     * Creates a virtual-thread client with a custom graceful-shutdown wait.
     * @since 5.6
     */
    public static CloseableHttpClient createVirtualThreadDefault(final TimeValue shutdownWait) {
        return HttpClientBuilder.create()
                .useVirtualThreads()
                .virtualThreadShutdownWait(shutdownWait)
                .build();
    }



}
