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
package org.apache.hc.client5.testing.caching.async;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.async.AbstractSimpleServerExchangeHandler;
import org.apache.hc.client5.testing.async.AuthenticatingAsyncDecorator;
import org.apache.hc.client5.testing.extension.async.CachingHttpAsyncClientResource;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncServerResources;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Async equivalent of {@code TestCachingClientIntegration}: an authenticated response must not
 * be served to a different execution context, and a response whose {@code Vary} header names
 * {@code Cookie} or {@code Authorization} must never be cached, since the cache cannot see the
 * value either header will carry once the request reaches the protocol layer.
 */
class TestCachingAsyncClientIntegration {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestAsyncServerResources serverResources;
    @RegisterExtension
    private final CachingHttpAsyncClientResource clientResource;

    private final AtomicInteger originHits = new AtomicInteger();
    private final AtomicInteger aliceHits = new AtomicInteger();
    private final AtomicInteger bobHits = new AtomicInteger();
    private final AtomicInteger anonymousHits = new AtomicInteger();

    TestCachingAsyncClientIntegration() throws Exception {
        this.serverResources = new TestAsyncServerResources(URIScheme.HTTP, TIMEOUT, ServerProtocolLevel.STANDARD);
        this.clientResource = new CachingHttpAsyncClientResource(HttpVersionPolicy.FORCE_HTTP_1);
        this.clientResource.configure(builder -> builder
                .setResourceFactory(HeapResourceFactory.INSTANCE));
    }

    HttpHost startServer() throws Exception {
        final InetSocketAddress address = serverResources.server().start();
        return new HttpHost(serverResources.scheme().id, "localhost", address.getPort());
    }

    // Authentication: a shared cache must not serve a response to an authenticated request
    // to a different execution context.

    /**
     * The test server doubles as a plain HTTP proxy, so requests are sent in absolute form
     * and the protocol layer authenticates a copy of the request passed down by the cache.
     */
    private HttpHost startServerAsProxy() throws Exception {
        final InetSocketAddress address = serverResources.server().start();
        clientResource.configure(builder -> builder
                .setProxy(new HttpHost(serverResources.scheme().id, "127.0.0.1", address.getPort())));
        return new HttpHost(serverResources.scheme().id, "localhost", address.getPort());
    }

    private void configureAuthenticatingServer() {
        serverResources.configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(exchangeHandler -> new AuthenticatingAsyncDecorator(
                        exchangeHandler, new BasicTestAuthenticator("test:test", "test realm")))
                .register("*", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) {
                        originHits.incrementAndGet();
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                        response.setBody("private data", ContentType.TEXT_PLAIN);
                        return response;
                    }

                }));
    }

    private SimpleHttpResponse execute(
            final CloseableHttpAsyncClient client,
            final HttpHost target,
            final HttpCacheContext context) throws Exception {
        return client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/private")
                        .build(), context, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
    }

    private void assertAuthenticatedResponse(
            final CloseableHttpAsyncClient client,
            final HttpHost target,
            final HttpCacheContext context) throws Exception {
        final SimpleHttpResponse response = execute(client, target, context);
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("private data", response.getBodyText());
    }

    private void assertNotServedToAnonymousContext(
            final CloseableHttpAsyncClient client,
            final HttpHost target) throws Exception {
        final SimpleHttpResponse response = execute(client, target, HttpCacheContext.create());
        Assertions.assertEquals(HttpStatus.SC_UNAUTHORIZED, response.getCode());
        Assertions.assertEquals(1, originHits.get());
    }

    @Test
    void testResponseToChallengedRequestNotServedToAnotherContext() throws Exception {
        configureAuthenticatingServer();
        final HttpHost target = startServerAsProxy();
        final CloseableHttpAsyncClient client = clientResource.client();

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
        final HttpHost target = startServerAsProxy();
        final CloseableHttpAsyncClient client = clientResource.client();

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

    private String fetch(final CloseableHttpAsyncClient client, final HttpHost target, final HttpCacheContext context) throws Exception {
        final SimpleHttpResponse response = client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/resource")
                        .build(), context, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        return response.getBodyText();
    }

    @Test
    void testCookieVaryResponseIsNeverCached() throws Exception {
        serverResources.configureServer(bootstrap -> bootstrap
                .register("*", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) {
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
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                        response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
                        response.setBody(body, ContentType.TEXT_PLAIN);
                        return response;
                    }

                }));
        final HttpHost target = startServer();
        final CloseableHttpAsyncClient client = clientResource.client();

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
                .register("*", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) {
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
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
                        response.setHeader(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);
                        response.setBody(body, ContentType.TEXT_PLAIN);
                        return response;
                    }

                }));
        final HttpHost target = startServer();
        final CloseableHttpAsyncClient client = clientResource.client();

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
