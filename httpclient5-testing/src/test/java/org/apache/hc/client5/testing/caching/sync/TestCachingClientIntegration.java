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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.classic.AuthenticatingDecorator;
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
 * Integration tests for request contexts a shared cache must keep apart: an authenticated
 * response must not be served to a different execution context, and a response whose
 * {@code Vary} header names {@code Cookie} or {@code Authorization} must never be cached at
 * all, since the cache sits above the point in the execution chain where either header is
 * added to the request and can never select the correct variant for them.
 */
class TestCachingClientIntegration {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger originHits = new AtomicInteger();
    private final AtomicInteger aliceHits = new AtomicInteger();
    private final AtomicInteger bobHits = new AtomicInteger();
    private final AtomicInteger anonymousHits = new AtomicInteger();

    TestCachingClientIntegration() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.clientResource = new CachingHttpClientResource();
        this.clientResource.configure(builder -> builder
                .setResourceFactory(HeapResourceFactory.INSTANCE));
    }

    HttpHost startServer() throws Exception {
        final InetSocketAddress address = serverResources.server().start();
        return new HttpHost(serverResources.scheme().id, "localhost", address.getPort());
    }

    // Authentication: a shared cache must not serve a response to an authenticated request
    // to a different execution context.

    private void configureAuthenticatingServer() {
        serverResources.configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new AuthenticatingDecorator(
                        requestHandler, new BasicTestAuthenticator("test:test", "test realm")))
                .register("*", (request, response, context) -> {
                    originHits.incrementAndGet();
                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                    response.setEntity(new StringEntity("private data", ContentType.TEXT_PLAIN));
                }));
    }

    private void assertAuthenticatedResponse(
            final CloseableHttpClient client,
            final HttpHost target,
            final HttpCacheContext context) throws Exception {
        client.execute(target, new HttpGet("/private"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertEquals("private data", EntityUtils.toString(response.getEntity(), 1000));
            return null;
        });
    }

    private void assertNotServedToAnonymousContext(
            final CloseableHttpClient client,
            final HttpHost target) throws Exception {
        client.execute(target, new HttpGet("/private"), HttpCacheContext.create(), response -> {
            Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });
        Assertions.assertEquals(1, originHits.get());
    }

    @Test
    void testResponseToChallengedRequestNotServedToAnotherContext() throws Exception {
        configureAuthenticatingServer();
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        final HttpCacheContext context = HttpCacheContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, "test", "test".toCharArray())
                .build());
        assertAuthenticatedResponse(client, target, context);

        assertNotServedToAnonymousContext(client, target);
    }

    @Test
    void testResponseToPreemptiveRequestNotServedToAnotherContext() throws Exception {
        configureAuthenticatingServer();
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials("test", "test".toCharArray()));
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        final HttpCacheContext context = HttpCacheContext.create();
        context.setAuthCache(authCache);
        assertAuthenticatedResponse(client, target, context);

        assertNotServedToAnonymousContext(client, target);
    }

    // Vary: a response whose Vary header names Cookie or Authorization must never be cached,
    // including repeated requests from the very same session or credentials.

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

    private HttpCacheContext preemptiveAuthContext(final HttpHost target, final String user) {
        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials(user, "secret".toCharArray()));
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        final HttpCacheContext context = HttpCacheContext.create();
        context.setAuthCache(authCache);
        return context;
    }

    private static String decodeBasicUser(final String authorizationHeaderValue) {
        final String credentials = new String(
                Base64.getDecoder().decode(authorizationHeaderValue.substring("Basic ".length())),
                StandardCharsets.UTF_8);
        return credentials.substring(0, credentials.indexOf(':'));
    }

    private String fetch(final CloseableHttpClient client, final HttpHost target, final HttpCacheContext context) throws Exception {
        return client.execute(target, new HttpGet("/resource"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity(), 1000);
        });
    }

    @Test
    void testCookieVaryResponseIsNeverCached() throws Exception {
        serverResources.configureServer(bootstrap -> bootstrap
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
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        final HttpCacheContext aliceContext = sessionContext(target, "alice-session");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(1, aliceHits.get());
        // a second request from the very same session must still reach the origin: nothing was cached
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(2, aliceHits.get());

        final HttpCacheContext bobContext = sessionContext(target, "bob-session");
        Assertions.assertEquals("bob's private content", fetch(client, target, bobContext));
        Assertions.assertEquals(1, bobHits.get());
        Assertions.assertEquals("bob's private content", fetch(client, target, bobContext));
        Assertions.assertEquals(2, bobHits.get());

        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());
        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(2, anonymousHits.get());
    }

    @Test
    void testAuthorizationVaryResponseIsNeverCached() throws Exception {
        serverResources.configureServer(bootstrap -> bootstrap
                .register("*", (request, response, context) -> {
                    final Header authorization = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                    final String body;
                    if (authorization == null) {
                        anonymousHits.incrementAndGet();
                        body = "anonymous content";
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
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        final HttpCacheContext aliceContext = preemptiveAuthContext(target, "alice");
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(1, aliceHits.get());
        // a second request with the very same credentials must still reach the origin: nothing was cached
        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext));
        Assertions.assertEquals(2, aliceHits.get());

        final HttpCacheContext bobContext = preemptiveAuthContext(target, "bob");
        Assertions.assertEquals("bob's private content", fetch(client, target, bobContext));
        Assertions.assertEquals(1, bobHits.get());
        Assertions.assertEquals("bob's private content", fetch(client, target, bobContext));
        Assertions.assertEquals(2, bobHits.get());

        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());
        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(2, anonymousHits.get());
    }

}
