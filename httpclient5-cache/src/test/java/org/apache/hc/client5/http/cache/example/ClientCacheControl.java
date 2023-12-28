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

import org.apache.hc.client5.http.cache.CacheContextBuilder;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpClients;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * This is an example demonstrating how to control execution of cache
 * operation and determine its outcome using the classic HTTP cache API.
 */
public class ClientCacheControl {

    public static void main(final String[] args) throws Exception {

        final HttpHost target = new HttpHost("https", "www.apache.org");

        try (final CloseableHttpClient httpclient = CachingHttpClients.custom()
                .setCacheConfig(CacheConfig.custom()
                        .setMaxObjectSize(200000)
                        .setHeuristicCachingEnabled(true)
                        .build())
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .build()) {

            final ClassicHttpRequest httpget1 = ClassicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();

            // Use default cache control
            final HttpCacheContext context = CacheContextBuilder.create()
                    .setCacheControl(RequestCacheControl.DEFAULT)
                    .build();

            System.out.println("Executing request " + httpget1.getMethod() + " " + httpget1.getUri());
            httpclient.execute(httpget1, context, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget1 + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
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
                return null;
            });

            final ClassicHttpRequest httpget2 = ClassicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();

            // Ensure a custom freshness for the cache entry
            context.setRequestCacheControl(RequestCacheControl.builder()
                    .setMinFresh(100)
                    .build());

            System.out.println("Executing request " + httpget2.getMethod() + " " + httpget2.getUri());
            httpclient.execute(httpget2, context, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget2 + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
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
                return null;
            });

            Thread.sleep(2000);

            final ClassicHttpRequest httpget3 = ClassicRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/")
                    .build();

            // Try to force cache entry re-validation
            context.setRequestCacheControl(RequestCacheControl.builder()
                    .setMaxAge(0)
                    .build());

            System.out.println("Executing request " + httpget3.getMethod() + " " + httpget3.getUri());
            httpclient.execute(httpget3, context, response -> {
                System.out.println("----------------------------------------");
                System.out.println(httpget3 + "->" + new StatusLine(response));
                EntityUtils.consume(response.getEntity());
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
                return null;
            });
        }


    }
}
