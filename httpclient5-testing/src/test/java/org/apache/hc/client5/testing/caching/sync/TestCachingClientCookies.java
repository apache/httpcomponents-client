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

import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
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
 * A response that varies on the Cookie header must not be served to an
 * execution context that sends different cookies.
 */
class TestCachingClientCookies {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger originHits = new AtomicInteger();

    TestCachingClientCookies() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("*", (request, response, context) -> {
                    originHits.incrementAndGet();
                    final Header cookie = request.getFirstHeader(HttpHeaders.COOKIE);
                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                    response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
                    response.setEntity(new StringEntity(
                            "page for " + (cookie != null ? cookie.getValue() : "anonymous"),
                            ContentType.TEXT_PLAIN));
                }));
        this.clientResource = new CachingHttpClientResource();
        this.clientResource.configure(builder -> builder
                .setResourceFactory(HeapResourceFactory.INSTANCE));
    }

    HttpHost startServer() throws Exception {
        final InetSocketAddress address = serverResources.server().start();
        return new HttpHost(serverResources.scheme().id, "localhost", address.getPort());
    }

    static HttpCacheContext createContext(final String session) {
        final HttpCacheContext context = HttpCacheContext.create();
        final BasicCookieStore cookieStore = new BasicCookieStore();
        if (session != null) {
            final BasicClientCookie cookie = new BasicClientCookie("SESSION", session);
            cookie.setDomain("localhost");
            cookie.setPath("/");
            cookieStore.addCookie(cookie);
        }
        context.setCookieStore(cookieStore);
        return context;
    }

    static void assertPage(
            final CloseableHttpClient client,
            final HttpHost target,
            final HttpCacheContext context,
            final String expected) throws Exception {
        client.execute(target, new HttpGet("/home"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertEquals(expected, EntityUtils.toString(response.getEntity()));
            return null;
        });
    }

    @Test
    void testResponseVaryingOnCookieNotServedToContextWithOtherCookies() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        assertPage(client, target, createContext("alice"), "page for SESSION=alice");
        assertPage(client, target, createContext("bob"), "page for SESSION=bob");
        assertPage(client, target, createContext(null), "page for anonymous");
        Assertions.assertEquals(3, originHits.get());

        assertPage(client, target, createContext(null), "page for anonymous");
        Assertions.assertEquals(3, originHits.get());
    }

}
