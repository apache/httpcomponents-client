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
package org.apache.hc.client5.testing.compatibility.sync;

import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.testing.extension.sync.CachingHttpClientResource;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class CachingHttpClientCompatibilityTest {

    private final HttpHost target;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    public CachingHttpClientCompatibilityTest(final HttpHost target) throws Exception {
        this.target = target;
        this.clientResource = new CachingHttpClientResource();
        this.clientResource.configure(builder -> builder
                .setCacheConfig(CacheConfig.custom()
                        .setMaxObjectSize(10240 * 16)
                        .build())
                .setResourceFactory(HeapResourceFactory.INSTANCE));
    }

    CloseableHttpClient client() {
        return clientResource.client();
    }

    @Test
    void test_options_ping() throws Exception {
        final CloseableHttpClient client = client();
        final HttpCacheContext context = HttpCacheContext.create();
        final HttpOptions options = new HttpOptions("*");
        try (ClassicHttpResponse response = client.executeOpen(target, options, context)) {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    void test_get_from_cache() throws Exception {
        final CloseableHttpClient client = client();
        final String[] resources1 = {"/111", "/222"};
        for (final String r : resources1) {
            final HttpCacheContext context1 = HttpCacheContext.create();
            final HttpGet httpGet1 = new HttpGet(r);
            try (ClassicHttpResponse response = client.executeOpen(target, httpGet1, context1)) {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertEquals(CacheResponseStatus.CACHE_MISS, context1.getCacheResponseStatus());
                final ResponseCacheControl responseCacheControl = context1.getResponseCacheControl();
                Assertions.assertNotNull(responseCacheControl);
                Assertions.assertEquals(600, responseCacheControl.getMaxAge());
                final HttpCacheEntry cacheEntry = context1.getCacheEntry();
                Assertions.assertNotNull(cacheEntry);
                EntityUtils.consume(response.getEntity());
            }
            final HttpCacheContext context2 = HttpCacheContext.create();
            final HttpGet httpGet2 = new HttpGet(r);
            try (ClassicHttpResponse response = client.executeOpen(target, httpGet2, context2)) {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertEquals(CacheResponseStatus.CACHE_HIT, context2.getCacheResponseStatus());
                final ResponseCacheControl responseCacheControl = context2.getResponseCacheControl();
                Assertions.assertNotNull(responseCacheControl);
                Assertions.assertEquals(600, responseCacheControl.getMaxAge());
                final HttpCacheEntry cacheEntry = context2.getCacheEntry();
                Assertions.assertNotNull(cacheEntry);
                Assertions.assertSame(cacheEntry, context1.getCacheEntry());
                EntityUtils.consume(response.getEntity());
            }

            Thread.sleep(2000);

            final HttpGet httpGet3 = new HttpGet(r);
            final HttpCacheContext context3 = HttpCacheContext.create();
            context3.setRequestCacheControl(RequestCacheControl.builder()
                    .setMaxAge(0)
                    .build());
            try (ClassicHttpResponse response = client.executeOpen(target, httpGet3, context3)) {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertEquals(CacheResponseStatus.VALIDATED, context3.getCacheResponseStatus());
                final HttpCacheEntry cacheEntry = context3.getCacheEntry();
                Assertions.assertNotNull(cacheEntry);
                Assertions.assertNotSame(cacheEntry, context1.getCacheEntry());
                EntityUtils.consume(response.getEntity());
            }
        }
        final String[] resources2 = {"/333"};
        for (final String r : resources2) {
            final HttpCacheContext context1 = HttpCacheContext.create();
            final HttpGet httpGet1 = new HttpGet(r);
            try (ClassicHttpResponse response = client.executeOpen(target, httpGet1, context1)) {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertEquals(CacheResponseStatus.CACHE_MISS, context1.getCacheResponseStatus());
                final ResponseCacheControl responseCacheControl = context1.getResponseCacheControl();
                Assertions.assertNotNull(responseCacheControl);
                Assertions.assertEquals(-1, responseCacheControl.getMaxAge());
                final HttpCacheEntry cacheEntry = context1.getCacheEntry();
                Assertions.assertNotNull(cacheEntry);
                EntityUtils.consume(response.getEntity());
            }
            final HttpCacheContext context2 = HttpCacheContext.create();
            final HttpGet httpGet2 = new HttpGet(r);
            try (ClassicHttpResponse response = client.executeOpen(target, httpGet2, context2)) {
                Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
                Assertions.assertEquals(CacheResponseStatus.VALIDATED, context2.getCacheResponseStatus());
                final ResponseCacheControl responseCacheControl = context2.getResponseCacheControl();
                Assertions.assertNotNull(responseCacheControl);
                Assertions.assertEquals(-1, responseCacheControl.getMaxAge());
                final HttpCacheEntry cacheEntry = context2.getCacheEntry();
                Assertions.assertNotNull(cacheEntry);
                Assertions.assertNotSame(cacheEntry, context1.getCacheEntry());
                EntityUtils.consume(response.getEntity());
            }
        }
    }

}
