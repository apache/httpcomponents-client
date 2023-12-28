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

import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.cache.CacheContextBuilder;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
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
 * This is an example demonstrating how to control execution of cache
 * operation and determine its outcome using the async HTTP cache API.
 */
public class AsyncClientCacheControl {

    public static void main(final String[] args) throws Exception {

        final HttpHost target = new HttpHost("https", "www.apache.org");

        try (final CloseableHttpAsyncClient httpclient = CachingHttpAsyncClients.custom()
                .setCacheConfig(CacheConfig.custom()
                        .setMaxObjectSize(200000)
                        .setHeuristicCachingEnabled(true)
                        .build())
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .build()) {

            httpclient.start();

            final SimpleHttpRequest httpget1 = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();

            // Use default cache control
            final HttpCacheContext context = CacheContextBuilder.create()
                    .setCacheControl(RequestCacheControl.DEFAULT)
                    .build();

            System.out.println("Executing request " + httpget1.getMethod() + " " + httpget1.getUri());
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleRequestProducer.create(httpget1),
                    SimpleResponseConsumer.create(),
                    context,
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(httpget1 + "->" + new StatusLine(response));
                            System.out.println("Cache status: " + context.getCacheResponseStatus());
                            System.out.println("Request cache control: " + context.getRequestCacheControl());
                            System.out.println("Response cache control: " + context.getResponseCacheControl());
                            final HttpCacheEntry cacheEntry = context.getCacheEntry();
                            if (cacheEntry != null) {
                                System.out.println("Cache entry resource: " + cacheEntry.getResource());
                                System.out.println("Date: " + cacheEntry.getInstant());
                                System.out.println("Expires: " + cacheEntry.getExpires());
                                System.out.println("Last modified: " + cacheEntry.getLastModified());
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(httpget1 + "->" + ex);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(httpget1 + " cancelled");
                        }

                    });
            future.get();

            final SimpleHttpRequest httpget2 = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();

            // Ensure a custom freshness for the cache entry
            context.setRequestCacheControl(RequestCacheControl.builder()
                    .setMinFresh(100)
                    .build());

            System.out.println("Executing request " + httpget2.getMethod() + " " + httpget2.getUri());
            final Future<SimpleHttpResponse> future2 = httpclient.execute(
                    SimpleRequestProducer.create(httpget2),
                    SimpleResponseConsumer.create(),
                    context,
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(httpget2 + "->" + new StatusLine(response));
                            System.out.println("Cache status: " + context.getCacheResponseStatus());
                            System.out.println("Request cache control: " + context.getRequestCacheControl());
                            System.out.println("Response cache control: " + context.getResponseCacheControl());
                            final HttpCacheEntry cacheEntry = context.getCacheEntry();
                            if (cacheEntry != null) {
                                System.out.println("Cache entry resource: " + cacheEntry.getResource());
                                System.out.println("Date: " + cacheEntry.getInstant());
                                System.out.println("Expires: " + cacheEntry.getExpires());
                                System.out.println("Last modified: " + cacheEntry.getLastModified());
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(httpget2 + "->" + ex);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(httpget2 + " cancelled");
                        }

                    });
            future2.get();

            Thread.sleep(2000);

            final SimpleHttpRequest httpget3 = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();

            // Try to force cache entry re-validation
            context.setRequestCacheControl(RequestCacheControl.builder()
                    .setMaxAge(0)
                    .build());

            System.out.println("Executing request " + httpget3.getMethod() + " " + httpget3.getUri());
            final Future<SimpleHttpResponse> future3 = httpclient.execute(
                    SimpleRequestProducer.create(httpget3),
                    SimpleResponseConsumer.create(),
                    context,
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(httpget3 + "->" + new StatusLine(response));
                            System.out.println("Cache status: " + context.getCacheResponseStatus());
                            System.out.println("Request cache control: " + context.getRequestCacheControl());
                            System.out.println("Response cache control: " + context.getResponseCacheControl());
                            final HttpCacheEntry cacheEntry = context.getCacheEntry();
                            if (cacheEntry != null) {
                                System.out.println("Cache entry resource: " + cacheEntry.getResource());
                                System.out.println("Date: " + cacheEntry.getInstant());
                                System.out.println("Expires: " + cacheEntry.getExpires());
                                System.out.println("Last modified: " + cacheEntry.getLastModified());
                            }
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(httpget3 + "->" + ex);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(httpget3 + " cancelled");
                        }

                    });
            future3.get();

            httpclient.close(CloseMode.GRACEFUL);
        }
    }
}
