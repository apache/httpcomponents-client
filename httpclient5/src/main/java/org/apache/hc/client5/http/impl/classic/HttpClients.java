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

import org.apache.hc.client5.http.impl.TooEarlyRetryStrategy;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;

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
     * Create a new {@link HttpClientBuilder} that preconfigures {@link TooEarlyRetryStrategy}.
     *
     * @param include429and503 when {@code true}, also retry {@code 429} and {@code 503} with {@code Retry-After}.
     * @since 5.6
     */
    public static HttpClientBuilder customTooEarlyAware(final boolean include429and503) {
        return HttpClientBuilder.create()
                .setRetryStrategy(new TooEarlyRetryStrategy(include429and503));
    }

    /**
     * Build a client with RFC 8470 handling baked in:
     * <ul>
     *   <li>Retry once on {@code 425 Too Early}.</li>
     *   <li>Also retry {@code 429} / {@code 503} respecting {@code Retry-After}.</li>
     *   <li>Retries only on idempotent methods and repeatable entities.</li>
     * </ul>
     * @since 5.6
     */
    public static CloseableHttpClient createDefaultTooEarlyAware() {
        return customTooEarlyAware(true).build();
    }

    /**
     * Same as {@link #createDefaultTooEarlyAware()} but also uses system properties.
     */
    public static CloseableHttpClient createSystemTooEarlyAware() {
        return customTooEarlyAware(true).useSystemProperties().build();
    }
}
