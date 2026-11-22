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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
 * The cache's own default-on backend-response freshness check ({@code CacheConfig#isFreshnessCheckEnabled})
 * re-matches the just-received response against the cache before deciding whether to store it, to avoid
 * clobbering a fresher entry a concurrent exchange might already have stored. That re-match must use the
 * request actually sent to the backend, exactly like the storage call right next to it: using the cache's
 * own, pre-Cookie request instead would match the wrong (e.g. anonymous) entry, and if that wrongly matched
 * entry happens to look newer than the response just received, serve its content back instead of the
 * response that was actually just fetched for this request.
 */
class TestCachingClientCookieVaryFreshnessCheck {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger aliceHits = new AtomicInteger();
    private final AtomicInteger anonymousHits = new AtomicInteger();

    TestCachingClientCookieVaryFreshnessCheck() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("*", (request, response, context) -> {
                    final Header cookie = request.getFirstHeader(HttpHeaders.COOKIE);
                    final String body;
                    if (cookie == null) {
                        anonymousHits.incrementAndGet();
                        body = "anonymous content";
                    } else {
                        aliceHits.incrementAndGet();
                        body = "alice's private content";
                        // An origin (or an intermediary) whose Date trails the one on the entry already
                        // cached for a *different* variant: harmless on its own, but it lets a wrong
                        // match (against that other variant) look "fresher" than the response that was
                        // actually just received for this request.
                        response.setHeader(HttpHeaders.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(
                                ZonedDateTime.now(ZoneOffset.UTC).minusHours(1)));
                    }
                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                    response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
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

    private HttpCacheContext sessionContext(final HttpHost target, final String sessionCookieValue) {
        final BasicCookieStore cookieStore = new BasicCookieStore();
        final BasicClientCookie cookie = new BasicClientCookie("session", sessionCookieValue);
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
        final HttpCacheContext context = HttpCacheContext.create();
        context.setCookieStore(cookieStore);
        return context;
    }

    private String fetch(final CloseableHttpClient client, final HttpHost target, final HttpCacheContext context) throws Exception {
        return client.execute(target, new HttpGet("/resource"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity());
        });
    }

    @Test
    void testBackendFreshnessCheckDoesNotServeAWronglyMatchedOlderVariant() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        // caches the anonymous variant with a Date of "now"
        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());

        // alice's own, brand new response (backdated Date header) must still be the one she gets back,
        // not the anonymous variant that a request-identity mismatch in the freshness check would match
        final HttpCacheContext aliceContext = sessionContext(target, "alice-session");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(1, aliceHits.get());
    }

}
