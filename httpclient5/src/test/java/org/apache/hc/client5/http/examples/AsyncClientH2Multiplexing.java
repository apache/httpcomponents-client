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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * Example of asynchronous HTTP/1.1 request execution with message exchange multiplexing
 * over HTTP/2 connections.
 */
public class AsyncClientH2Multiplexing {

    public static void main(final String[] args) throws Exception {

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(Timeout.ofSeconds(5))
                .build();

        final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                        .build())
                .setMessageMultiplexing(true)
                .build();

        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(connectionManager)
                .setIOReactorConfig(ioReactorConfig)
                .build();

        client.start();

        final HttpHost target = new HttpHost("https", "nghttp2.org");

        final SimpleHttpRequest warmup = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/httpbin")
                .build();

        // Make sure there is an open HTTP/2 connection in the pool
        System.out.println("Executing warm-up request " + warmup);
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestProducer.create(warmup),
                SimpleResponseConsumer.create(),
                new FutureCallback<SimpleHttpResponse>() {

                    @Override
                    public void completed(final SimpleHttpResponse response) {
                        System.out.println(warmup + "->" + new StatusLine(response));
                        System.out.println(response.getBody());
                    }

                    @Override
                    public void failed(final Exception ex) {
                        System.out.println(warmup + "->" + ex);
                    }

                    @Override
                    public void cancelled() {
                        System.out.println(warmup + " cancelled");
                    }

                });
        future.get();

        Thread.sleep(1000);

        System.out.println("Connection pool stats: " + connectionManager.getTotalStats());

        // Execute multiple requests over the HTTP/2 connection from the pool
        final String[] requestUris = new String[]{"/httpbin", "/httpbin/ip", "/httpbin/user-agent", "/httpbin/headers"};
        final CountDownLatch countDownLatch = new CountDownLatch(requestUris.length);

        for (final String requestUri : requestUris) {
            final SimpleHttpRequest request = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(requestUri)
                    .build();

            System.out.println("Executing request " + request);
            client.execute(
                    SimpleRequestProducer.create(request),
                    SimpleResponseConsumer.create(),
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            countDownLatch.countDown();
                            System.out.println(request + "->" + new StatusLine(response));
                            System.out.println(response.getBody());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            countDownLatch.countDown();
                            System.out.println(request + "->" + ex);
                        }

                        @Override
                        public void cancelled() {
                            countDownLatch.countDown();
                            System.out.println(request + " cancelled");
                        }

                    });
        }

        countDownLatch.await();

        // There still should be a single connection in the pool
        System.out.println("Connection pool stats: " + connectionManager.getTotalStats());

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
    }

}
