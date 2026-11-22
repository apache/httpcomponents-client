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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
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
 * A cached response that varies on {@code Authorization} must be matched against the credentials a
 * request is actually going to authenticate with, not against the request as it exists above the caching
 * layer (where {@code Authorization} has not been added yet). Otherwise a response fetched for one user
 * gets cached as the "no credentials" variant and can be served back to a different user.
 */
class TestCachingClientAuthorizationVary {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger aliceHits = new AtomicInteger();
    private final AtomicInteger bobHits = new AtomicInteger();
    private final AtomicInteger anonymousHits = new AtomicInteger();
    private final Map<String, AtomicInteger> bearerHits = new ConcurrentHashMap<>();

    TestCachingClientAuthorizationVary() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("*", (request, response, context) -> {
                    final Header authorization = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                    final String body;
                    if (authorization == null) {
                        anonymousHits.incrementAndGet();
                        body = "anonymous content";
                    } else if (authorization.getValue().startsWith("Bearer ")) {
                        bearerHits.computeIfAbsent(authorization.getValue(), k -> new AtomicInteger()).incrementAndGet();
                        body = "bearer:" + authorization.getValue();
                    } else if (decodeBasicUser(authorization.getValue()).equals("alice")) {
                        aliceHits.incrementAndGet();
                        body = "alice's private content";
                    } else {
                        bobHits.incrementAndGet();
                        body = "bob's private content";
                    }
                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
                    response.setHeader(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);
                    response.setEntity(new StringEntity(body, ContentType.TEXT_PLAIN));
                }));
        this.clientResource = new CachingHttpClientResource();
        this.clientResource.configure(builder -> builder
                .setResourceFactory(HeapResourceFactory.INSTANCE));
    }

    private static String decodeBasicUser(final String authorizationHeaderValue) {
        final String credentials = new String(
                Base64.getDecoder().decode(authorizationHeaderValue.substring("Basic ".length())),
                StandardCharsets.UTF_8);
        return credentials.substring(0, credentials.indexOf(':'));
    }

    HttpHost startServer() throws Exception {
        final InetSocketAddress address = serverResources.server().start();
        return new HttpHost(serverResources.scheme().id, "localhost", address.getPort());
    }

    private HttpCacheContext preemptiveContext(final HttpHost target, final String user) {
        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials(user, "secret".toCharArray()));
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        final HttpCacheContext context = HttpCacheContext.create();
        context.setAuthCache(authCache);
        return context;
    }

    private String fetch(final CloseableHttpClient client, final HttpHost target, final HttpCacheContext context) throws Exception {
        return client.execute(target, new HttpGet("/resource"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity());
        });
    }

    private String fetch(final CloseableHttpClient client, final HttpHost target, final String authorizationHeaderValue) throws Exception {
        final HttpGet request = new HttpGet("/resource");
        request.setHeader(HttpHeaders.AUTHORIZATION, authorizationHeaderValue);
        return client.execute(target, request, HttpCacheContext.create(), response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity());
        });
    }

    @Test
    void testEachUserGetsItsOwnCachedVariant() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        final HttpCacheContext aliceContext = preemptiveContext(target, "alice");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(1, aliceHits.get());

        final HttpCacheContext bobContext = preemptiveContext(target, "bob");
        Assertions.assertEquals("bob's private content", fetch(client, target, bobContext));
        Assertions.assertEquals(1, bobHits.get());

        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());
    }

    @Test
    void testAnonymousRequestThenPreemptiveUserGetsItsOwnVariant() throws Exception {
        // the leak in the other direction: an anonymous variant is cached first, then a preemptively
        // authenticated request for the same URI must not be served that anonymous variant back
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());

        final HttpCacheContext aliceContext = preemptiveContext(target, "alice");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(1, aliceHits.get());
    }

    @Test
    void testRepeatedPreemptiveRequestFromSameContextIsServedFromCache() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        final HttpCacheContext aliceContext = preemptiveContext(target, "alice");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));

        // one origin hit for three identical requests from the same, already-authenticated context:
        // the second and third must be served from the cached alice variant, not forwarded to the backend
        Assertions.assertEquals(1, aliceHits.get());
    }

    /**
     * The variant key must treat the {@code Authorization} value as opaque and case-sensitive: two
     * bearer tokens differing only in letter case are different credentials and must never share a
     * cached variant, or one of them would be served the other's response.
     */
    @Test
    void testDistinctCaseBearerTokensGetDistinctVariants() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        Assertions.assertEquals("bearer:Bearer TokenA", fetch(client, target, "Bearer TokenA"));
        Assertions.assertEquals("bearer:Bearer tokena", fetch(client, target, "Bearer tokena"));

        Assertions.assertEquals(1, bearerHits.get("Bearer TokenA").get());
        Assertions.assertEquals(1, bearerHits.get("Bearer tokena").get());
    }

}
