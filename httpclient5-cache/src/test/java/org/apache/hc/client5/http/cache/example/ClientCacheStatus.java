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
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpClients;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.StatusLine;

/**
 * This example demonstrates the RFC 9211 {@code Cache-Status} response header, which reports how
 * the cache handled each exchange. It is opt-in through
 * {@link CacheConfig.Builder#setCacheStatusEnabled(boolean)}.
 */
public class ClientCacheStatus {

    public static void main(final String[] args) throws Exception {

        final HttpHost target = new HttpHost("https", "www.apache.org");

        try (final CloseableHttpClient httpclient = CachingHttpClients.custom()
                .setCacheConfig(CacheConfig.custom()
                        .setMaxObjectSize(200000)
                        .setHeuristicCachingEnabled(true)
                        .setCacheStatusEnabled(true)
                        .build())
                .setResourceFactory(HeapResourceFactory.INSTANCE)
                .build()) {

            final HttpCacheContext context = CacheContextBuilder.create()
                    .setCacheControl(RequestCacheControl.DEFAULT)
                    .build();

            // The first request is forwarded to the origin (fwd=miss); a fresh, cacheable response
            // then lets the second identical request be served from the cache (hit).
            for (int i = 1; i <= 2; i++) {
                final ClassicHttpRequest httpget = ClassicRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/")
                        .build();

                System.out.println("Executing request " + httpget.getMethod() + " " + httpget.getUri());
                httpclient.execute(httpget, context, response -> {
                    System.out.println("----------------------------------------");
                    System.out.println(httpget + "->" + new StatusLine(response));
                    EntityUtils.consume(response.getEntity());
                    final Header cacheStatus = response.getFirstHeader("Cache-Status");
                    System.out.println("Cache-Status header: "
                            + (cacheStatus != null ? cacheStatus.getValue() : "<none>"));
                    System.out.println("Cache response status: " + context.getCacheResponseStatus());
                    return null;
                });
            }
        }
    }

}
