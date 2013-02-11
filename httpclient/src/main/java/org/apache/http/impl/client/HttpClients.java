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

package org.apache.http.impl.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.annotation.Immutable;
import org.apache.http.client.HttpClient;
import org.apache.http.client.async.HttpAsyncClientWithFuture;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * @since 4.3
 */
@Immutable
public class HttpClients {

    private HttpClients() {
        super();
    }

    public static HttpClientBuilder custom() {
        return HttpClientBuilder.create();
    }

    public static CloseableHttpClient createDefault() {
        return HttpClientBuilder.create().build();
    }

    public static CloseableHttpClient createSystem() {
        return HttpClientBuilder.create().useSystemProperties().build();
    }

    public static CloseableHttpClient createMinimal() {
        return new MinimalHttpClient(new PoolingHttpClientConnectionManager());
    }

    public static CloseableHttpClient createMinimal(final HttpClientConnectionManager connManager) {
        return new MinimalHttpClient(connManager);
    }

    /**
     * Creates a simple HttpAsyncClientWithFuture with an executor with the specified number of threads and a matching httpclient.
     * @param threads
     *            the number of connections and threads used for the httpclient and the executor used by @see
     *            HttpAsyncClientWithFuture.
     * @return a HttpAsyncClientWithFuture with an httpclient and executor that can handle the specified amount of
     *         threads/connections.
     */
    public static HttpAsyncClientWithFuture createAsync(int threads) {
        HttpClient httpClient = HttpClientBuilder.create().setMaxConnPerRoute(5).setMaxConnTotal(5).build();
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        return new HttpAsyncClientWithFuture(httpClient, executorService);
    }
}
