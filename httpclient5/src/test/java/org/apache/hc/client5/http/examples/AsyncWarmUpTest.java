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


package org.apache.hc.client5.http.examples;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;


/**
 * Demonstrates the usage of the {@link PoolingAsyncClientConnectionManager} to perform a warm-up
 * of connections asynchronously and execute an HTTP request using the Apache HttpClient 5 async API.
 *
 * <p>The warm-up process initializes a specified number of connections to a target host asynchronously,
 * ensuring they are ready for use before actual requests are made. The example then performs an
 * HTTP GET request to a target server and logs the response details.</p>
 *
 * <p>Key steps include:</p>
 * <ul>
 *     <li>Creating a {@link PoolingAsyncClientConnectionManager} instance with TLS configuration.</li>
 *     <li>Calling {@link PoolingAsyncClientConnectionManager#warmUp(HttpHost, Timeout, FutureCallback)}
 *     to prepare the connection pool for the specified target host.</li>
 *     <li>Waiting for the warm-up to complete using a {@link CompletableFuture}.</li>
 *     <li>Executing an HTTP GET request using {@link CloseableHttpAsyncClient}.</li>
 *     <li>Handling the HTTP response and logging protocol, SSL details, and response body.</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>
 * java AsyncWarmUpTest
 * </pre>
 *
 * <p>Dependencies: Ensure the required Apache HttpClient libraries are on the classpath.</p>
 */

public class AsyncWarmUpTest {

    public static void main(final String[] args) throws Exception {
        final PoolingAsyncClientConnectionManager cm = PoolingAsyncClientConnectionManagerBuilder.create()
                .build();

        // Perform the warm-up directly using a Future
        final CompletableFuture<Void> warmUpFuture = new CompletableFuture<>();
        cm.warmUp(new HttpHost("http", "httpbin.org", 80), Timeout.ofSeconds(10), new FutureCallback<Void>() {
            @Override
            public void completed(final Void result) {
                warmUpFuture.complete(null);
            }

            @Override
            public void failed(final Exception ex) {
                warmUpFuture.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                warmUpFuture.cancel(true);
            }
        });

        // Wait for the warm-up to complete
        try {
            warmUpFuture.get(10, TimeUnit.SECONDS);
            System.out.println("Warm-up completed successfully.");
        } catch (final Exception ex) {
            System.err.println("Warm-up failed: " + ex.getMessage());
            return; // Exit if warm-up fails
        }

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(cm)
                .build()) {

            client.start();

            final HttpHost target = new HttpHost("https", "httpbin.org");
            final HttpClientContext clientContext = HttpClientContext.create();

            final SimpleHttpRequest request = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();

            System.out.println("Executing request " + request);
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestProducer.create(request),
                    SimpleResponseConsumer.create(),
                    clientContext,
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(request + "->" + new StatusLine(response));
                            System.out.println("HTTP protocol " + clientContext.getProtocolVersion());
                            System.out.println(response.getBody());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(request + "->" + ex);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(request + " cancelled");
                        }

                    });
            future.get();

            System.out.println("Shutting down");
            client.close(CloseMode.GRACEFUL);
        }
    }
}
