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
package org.apache.hc.client5.http.cache.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.cache.CacheContextBuilder;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpAsyncClients;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;

/**
 * This is an example demonstrating how to enable request collapsing in the async HTTP cache.
 * When enabled, concurrent requests for the same cache key are coalesced so that only one
 * request goes to the backend while the others wait and then re-check the cache.
 */
public class AsyncClientCacheRequestCollapsing {

    public static void main(final String[] args) throws Exception {

        final HttpHost target = new HttpHost("https", "www.apache.org");

        try (final CloseableHttpAsyncClient httpclient = CachingHttpAsyncClients.custom()
                .setCacheConfig(CacheConfig.custom()
                        .setMaxObjectSize(200000)
                        .setHeuristicCachingEnabled(true)
                        .setRequestCollapsingEnabled(true)
                        .build())
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .build()) {

            httpclient.start();

            final int burst = 5;
            final List<Future<SimpleHttpResponse>> futures = new ArrayList<>(burst);

            for (int i = 0; i < burst; i++) {
                final SimpleHttpRequest httpget = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/")
                        .build();

                // One context per request: HttpCacheContext is not thread-safe.
                final HttpCacheContext context = CacheContextBuilder.create()
                        .setCacheControl(RequestCacheControl.DEFAULT)
                        .build();

                System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());
                futures.add(httpclient.execute(
                        SimpleRequestProducer.create(httpget),
                        SimpleResponseConsumer.create(),
                        context,
                        new FutureCallback<SimpleHttpResponse>() {

                            @Override
                            public void completed(final SimpleHttpResponse response) {
                                System.out.println(httpget + "->" + new StatusLine(response));
                                System.out.println("Cache status: " + context.getCacheResponseStatus());
                            }

                            @Override
                            public void failed(final Exception ex) {
                                System.out.println(httpget + "->" + ex);
                            }

                            @Override
                            public void cancelled() {
                                System.out.println(httpget + " cancelled");
                            }

                        }));
            }

            for (final Future<SimpleHttpResponse> future : futures) {
                future.get();
            }

            httpclient.close(CloseMode.GRACEFUL);
        }
    }
}
