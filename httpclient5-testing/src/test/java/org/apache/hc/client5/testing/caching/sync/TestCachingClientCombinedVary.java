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
 * A cached response that varies on both {@code Cookie} and {@code Authorization} at once must be matched
 * against both headers as the request is actually going to send/authenticate with them, in either order of
 * arrival: an anonymous variant cached first must not be served back to a request that is about to carry
 * either header, and a logged-in variant must not be served back to one that no longer does.
 */
class TestCachingClientCombinedVary {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger aliceHits = new AtomicInteger();
    private final AtomicInteger anonymousHits = new AtomicInteger();

    TestCachingClientCombinedVary() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("*", (request, response, context) -> {
                    final Header cookie = request.getFirstHeader(HttpHeaders.COOKIE);
                    final Header authorization = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                    final String body;
                    if (cookie != null && cookie.getValue().contains("alice")
                            && authorization != null && decodeBasicUser(authorization.getValue()).equals("alice")) {
                        aliceHits.incrementAndGet();
                        body = "alice's private content";
                    } else if (cookie == null && authorization == null) {
                        anonymousHits.incrementAndGet();
                        body = "anonymous content";
                    } else {
                        body = "mixed content";
                    }
                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
                    response.setHeader(HttpHeaders.VARY, "Cookie, Authorization");
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

    private HttpCacheContext aliceContext(final HttpHost target) {
        final BasicCookieStore cookieStore = new BasicCookieStore();
        final BasicClientCookie cookie = new BasicClientCookie("session", "alice-session");
        cookie.setDomain(target.getHostName());
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
        final BasicScheme basicScheme = new BasicScheme();
        basicScheme.initPreemptive(new UsernamePasswordCredentials("alice", "secret".toCharArray()));
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(target, basicScheme);
        final HttpCacheContext context = HttpCacheContext.create();
        context.setCookieStore(cookieStore);
        context.setAuthCache(authCache);
        return context;
    }

    private String fetch(final CloseableHttpClient client, final HttpHost target, final HttpCacheContext context) throws Exception {
        return client.execute(target, new HttpGet("/resource"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity());
        });
    }

    @Test
    void testAnonymousRequestThenFullyAuthenticatedSessionGetsItsOwnVariant() throws Exception {
        // the anonymous variant (no Cookie, no Authorization) is cached first, then alice's request,
        // carrying both headers, must not be served the anonymous variant back
        final HttpHost target = startServer();
        final CloseableHttpClient client = clientResource.client();

        Assertions.assertEquals("anonymous content", fetch(client, target, HttpCacheContext.create()));
        Assertions.assertEquals(1, anonymousHits.get());

        Assertions.assertEquals("alice's private content", fetch(client, target, aliceContext(target)));
        Assertions.assertEquals(1, aliceHits.get());
    }

}
