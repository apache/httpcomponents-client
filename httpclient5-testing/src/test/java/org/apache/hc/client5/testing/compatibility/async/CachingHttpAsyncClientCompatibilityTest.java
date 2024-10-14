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
package org.apache.hc.client5.testing.compatibility.async;

import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.cache.CacheResponseStatus;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.RequestCacheControl;
import org.apache.hc.client5.http.cache.ResponseCacheControl;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.testing.extension.async.CachingHttpAsyncClientResource;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class CachingHttpAsyncClientCompatibilityTest {

    static final Timeout TIMEOUT = Timeout.ofSeconds(5);

    private final HttpHost target;
    @RegisterExtension
    private final CachingHttpAsyncClientResource clientResource;

    public CachingHttpAsyncClientCompatibilityTest(final HttpVersionPolicy versionPolicy, final HttpHost target) throws Exception {
        this.target = target;
        this.clientResource = new CachingHttpAsyncClientResource(versionPolicy);
        this.clientResource.configure(builder -> builder
                .setCacheConfig(CacheConfig.custom()
                        .setMaxObjectSize(10240 * 16)
                        .build())
                .setResourceFactory(HeapResourceFactory.INSTANCE));
    }

    CloseableHttpAsyncClient client() {
        return clientResource.client();
    }

    @Test
    @Disabled
    void test_options_ping() throws Exception {
        final CloseableHttpAsyncClient client = client();
        final HttpCacheContext context = HttpCacheContext.create();
        final SimpleHttpRequest options = SimpleRequestBuilder.options()
                .setHttpHost(target)
                .setPath("*")
                .build();
        final Future<SimpleHttpResponse> future = client.execute(options, context, null);
        final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
    }

    @Test
    void test_get_from_cache() throws Exception {
        final CloseableHttpAsyncClient client = client();
        final String[] resources1 = {"/111", "/222"};
        for (final String r: resources1) {
            final SimpleHttpRequest httpGet1 = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(r)
                    .build();
            final HttpCacheContext context1 = HttpCacheContext.create();
            final Future<SimpleHttpResponse> future1 = client.execute(httpGet1, context1, null);
            final SimpleHttpResponse response1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());
            Assertions.assertEquals(CacheResponseStatus.CACHE_MISS, context1.getCacheResponseStatus());
            final ResponseCacheControl responseCacheControl1 = context1.getResponseCacheControl();
            Assertions.assertNotNull(responseCacheControl1);
            if (!r.equals("/333")) {
                Assertions.assertEquals(600, responseCacheControl1.getMaxAge());
            }
            final HttpCacheEntry cacheEntry1 = context1.getCacheEntry();
            Assertions.assertNotNull(cacheEntry1);

            final SimpleHttpRequest httpGet2 = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(r)
                    .build();
            final HttpCacheContext context2 = HttpCacheContext.create();
            final Future<SimpleHttpResponse> future2 = client.execute(httpGet2, context2, null);
            final SimpleHttpResponse response2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
            Assertions.assertEquals(CacheResponseStatus.CACHE_HIT, context2.getCacheResponseStatus());
            final ResponseCacheControl responseCacheControl2 = context2.getResponseCacheControl();
            Assertions.assertNotNull(responseCacheControl2);
            Assertions.assertEquals(600, responseCacheControl2.getMaxAge());
            final HttpCacheEntry cacheEntry2 = context2.getCacheEntry();
            Assertions.assertNotNull(cacheEntry2);
            Assertions.assertSame(cacheEntry2, context1.getCacheEntry());

            Thread.sleep(2000);

            final SimpleHttpRequest httpGet3 = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(r)
                    .build();
            final HttpCacheContext context3 = HttpCacheContext.create();
            context3.setRequestCacheControl(RequestCacheControl.builder()
                    .setMaxAge(0)
                    .build());
            final Future<SimpleHttpResponse> future3 = client.execute(httpGet3, context3, null);
            final SimpleHttpResponse response3 = future3.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertEquals(HttpStatus.SC_OK, response3.getCode());
            Assertions.assertEquals(CacheResponseStatus.VALIDATED, context3.getCacheResponseStatus());
            final HttpCacheEntry cacheEntry3 = context3.getCacheEntry();
            Assertions.assertNotNull(cacheEntry3);
            Assertions.assertNotSame(cacheEntry3, context1.getCacheEntry());
        }
        final String[] resources2 = {"/333"};
        for (final String r: resources2) {
            final SimpleHttpRequest httpGet1 = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(r)
                    .build();
            final HttpCacheContext context1 = HttpCacheContext.create();
            final Future<SimpleHttpResponse> future1 = client.execute(httpGet1, context1, null);
            final SimpleHttpResponse response1 = future1.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertEquals(HttpStatus.SC_OK, response1.getCode());
            Assertions.assertEquals(CacheResponseStatus.CACHE_MISS, context1.getCacheResponseStatus());
            final ResponseCacheControl responseCacheControl1 = context1.getResponseCacheControl();
            Assertions.assertNotNull(responseCacheControl1);
            Assertions.assertEquals(-1, responseCacheControl1.getMaxAge());
            final HttpCacheEntry cacheEntry1 = context1.getCacheEntry();
            Assertions.assertNotNull(cacheEntry1);

            final SimpleHttpRequest httpGet2 = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(r)
                    .build();
            final HttpCacheContext context2 = HttpCacheContext.create();
            final Future<SimpleHttpResponse> future2 = client.execute(httpGet2, context2, null);
            final SimpleHttpResponse response2 = future2.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
            Assertions.assertEquals(HttpStatus.SC_OK, response2.getCode());
            Assertions.assertEquals(CacheResponseStatus.VALIDATED, context2.getCacheResponseStatus());
            final ResponseCacheControl responseCacheControl2 = context2.getResponseCacheControl();
            Assertions.assertNotNull(responseCacheControl2);
            Assertions.assertEquals(-1, responseCacheControl2.getMaxAge());
            final HttpCacheEntry cacheEntry2 = context2.getCacheEntry();
            Assertions.assertNotNull(cacheEntry2);
            Assertions.assertNotSame(cacheEntry2, context1.getCacheEntry());
        }
    }

}
