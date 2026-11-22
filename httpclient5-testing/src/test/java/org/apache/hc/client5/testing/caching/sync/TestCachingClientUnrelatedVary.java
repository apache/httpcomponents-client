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
package org.apache.hc.client5.testing.caching.sync;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.extension.sync.CachingHttpClientResource;
import org.apache.hc.client5.testing.extension.sync.TestServerResources;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * A response that varies on a header that is neither {@code Cookie} nor {@code Authorization} (here,
 * {@code User-Agent}, explicitly set above the cache by the caller) must keep caching exactly as it did
 * before this fix: this fix is deliberately scoped to {@code Cookie} and {@code Authorization} only, and
 * must not change how any other {@code Vary} header, including one the client adds itself or a user's own
 * interceptor adds, gets matched. This is the regression #958 introduced for a generalized guard; this
 * fix must not reproduce it.
 */
class TestCachingClientUnrelatedVary {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger desktopHits = new AtomicInteger();
    private final AtomicInteger mobileHits = new AtomicInteger();

    TestCachingClientUnrelatedVary() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("*", (request, response, context) -> {
                    final Header userAgent = request.getFirstHeader(HttpHeaders.USER_AGENT);
                    final String body;
                    if (userAgent != null && userAgent.getValue().contains("mobile")) {
                        mobileHits.incrementAndGet();
                        body = "mobile layout";
                    } else {
                        desktopHits.incrementAndGet();
                        body = "desktop layout";
                    }
                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                    response.setHeader(HttpHeaders.VARY, HttpHeaders.USER_AGENT);
                    response.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
                }));
        this.clientResource = new CachingHttpClientResource();
        this.clientResource.configure(builder -> builder
                .setResourceFactory(HeapResourceFactory.INSTANCE));
    }

    HttpHost startServer() throws Exception {
        final InetSocketAddress address = serverResources.server().start();
        return new HttpHost(serverResources.scheme().id, "localhost", address.getPort());
    }

    private String fetch(final CloseableHttpClient client, final HttpHost target, final String userAgent) throws Exception {
        final HttpGet request = new HttpGet("/page");
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom().build());
        request.setHeader(HttpHeaders.USER_AGENT, userAgent);
        return client.execute(target, request, context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity());
        });
    }

    @Test
    void testUserAgentVariantsAreCachedAndReused() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        Assertions.assertEquals("desktop layout", fetch(client, target, "desktop-browser/1.0"));
        Assertions.assertEquals("mobile layout", fetch(client, target, "mobile-browser/1.0"));

        // repeat requests for each variant: served from cache, no further origin hits
        Assertions.assertEquals("desktop layout", fetch(client, target, "desktop-browser/1.0"));
        Assertions.assertEquals("mobile layout", fetch(client, target, "mobile-browser/1.0"));

        Assertions.assertEquals(1, desktopHits.get());
        Assertions.assertEquals(1, mobileHits.get());
    }

}
