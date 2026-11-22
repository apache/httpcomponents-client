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
 * A cached response that varies on {@code Cookie} must be matched against the cookies a request is
 * actually going to send, not against the request as it exists above the caching layer (where
 * {@code Cookie} has not been added yet). Otherwise a response fetched for one user's session gets
 * cached as the "no cookie" variant and can be served back to a different user, or to an anonymous
 * request that never had a session cookie at all.
 */
class TestCachingClientCookieVary {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger aliceHits = new AtomicInteger();
    private final AtomicInteger bobHits = new AtomicInteger();
    private final AtomicInteger anonymousHits = new AtomicInteger();

    TestCachingClientCookieVary() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("*", (request, response, context) -> {
                    final Header cookie = request.getFirstHeader(HttpHeaders.COOKIE);
                    final String body;
                    if (cookie == null) {
                        anonymousHits.incrementAndGet();
                        body = "anonymous content";
                    } else if (cookie.getValue().contains("alice")) {
                        aliceHits.incrementAndGet();
                        body = "alice's private content";
                    } else {
                        bobHits.incrementAndGet();
                        body = "bob's private content";
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
    void testEachSessionGetsItsOwnCachedVariant() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        final HttpCacheContext aliceContext = sessionContext(target, "alice-session");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(1, aliceHits.get());

        final HttpCacheContext bobContext = sessionContext(target, "bob-session");
        Assertions.assertEquals("bob's private content", fetch(client, target, bobContext));
        Assertions.assertEquals(1, bobHits.get());

        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());
    }

    @Test
    void testAnonymousRequestThenSessionGetsItsOwnVariant() throws Exception {
        // the leak in the other direction: an anonymous variant is cached first, then a session
        // request for the same URI must not be served that anonymous variant back
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());

        final HttpCacheContext aliceContext = sessionContext(target, "alice-session");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(1, aliceHits.get());
    }

    @Test
    void testLoggingInWithinSameContextGetsItsOwnVariant() throws Exception {
        // one context, first anonymous, then logged in: the session cookie is added to the same
        // cookie store the context already carries, between the two requests
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();
        final HttpCacheContext context = HttpCacheContext.create();
        context.setCookieStore(new BasicCookieStore());

        Assertions.assertEquals("anonymous content", fetch(client, target, context));
        final BasicClientCookie cookie = new BasicClientCookie("session", "alice-session");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");
        context.getCookieStore().addCookie(cookie);
        Assertions.assertEquals("alice's private content", fetch(client, target, context));

        Assertions.assertEquals(1, anonymousHits.get());
        Assertions.assertEquals(1, aliceHits.get());
    }

    @Test
    void testRepeatedRequestFromSameSessionIsServedFromCache() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        final HttpCacheContext aliceContext = sessionContext(target, "alice-session");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));

        // one origin hit for three identical requests from the same session: the second and third
        // must be served from the cached alice variant, not forwarded to the backend
        Assertions.assertEquals(1, aliceHits.get());
    }

}
