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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.testing.async.AbstractSimpleServerExchangeHandler;
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
 * Async equivalent of {@code TestCachingClientCookieVaryFreshnessCheck}. On {@code upstream/master} this
 * passed only by accident: the async chain used to mutate the cache's own held request in place as it
 * flowed down through the lower layers, so it picked up the real {@code Cookie} value by that side effect
 * by the time {@code AsyncCachingExec.BackendResponseHandler.completed()} ran its own freshness-check
 * match. Sending a copy down the chain instead (this branch's own, separately correct fix) removes that
 * side effect, so the freshness check's match call must use the same {@code effectiveRequest} the storage
 * call right next to it already does.
 */
class TestCachingAsyncClientCookieVaryFreshnessCheck {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestAsyncServerResources serverResources;
    @RegisterExtension
    private final CachingHttpAsyncClientResource clientResource;

    private final AtomicInteger aliceHits = new AtomicInteger();
    private final AtomicInteger anonymousHits = new AtomicInteger();

    TestCachingAsyncClientCookieVaryFreshnessCheck() throws Exception {
        this.serverResources = new TestAsyncServerResources(URIScheme.HTTP, TIMEOUT, ServerProtocolLevel.STANDARD);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("*", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) {
                        final Header cookie = request.getFirstHeader(HttpHeaders.COOKIE);
                        final String body;
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                        if (cookie == null) {
                            anonymousHits.incrementAndGet();
                            body = "anonymous content";
                        } else {
                            aliceHits.incrementAndGet();
                            body = "alice's private content";
                            // An origin (or an intermediary) whose Date trails the one on the entry
                            // already cached for a *different* variant: harmless on its own, but it lets
                            // a wrong match (against that other variant) look "fresher" than the response
                            // that was actually just received for this request.
                            response.setHeader(HttpHeaders.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(
                                    ZonedDateTime.now(ZoneOffset.UTC).minusHours(1)));
                        }
                        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                        response.setHeader(HttpHeaders.VARY, HttpHeaders.COOKIE);
                        response.setBody(body, ContentType.TEXT_PLAIN);
                        return response;
                    }

                }));
        this.clientResource = new CachingHttpAsyncClientResource(HttpVersionPolicy.FORCE_HTTP_1);
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
    void testBackendFreshnessCheckDoesNotServeAWronglyMatchedOlderVariant() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpAsyncClient client = clientResource.client();

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
