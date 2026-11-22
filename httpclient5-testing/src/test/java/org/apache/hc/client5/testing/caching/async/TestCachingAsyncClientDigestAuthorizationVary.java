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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
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
 * Async equivalent of {@code TestCachingClientDigestAuthorizationVary}: {@code Digest} is not
 * {@code @AuthStateCacheable}, so a request from a context that has already Digest-authenticated to
 * the target host must never be served a {@code Vary: Authorization} variant cached for an anonymous
 * request; the cache cannot preview what it would send, so it must go to the backend instead.
 */
class TestCachingAsyncClientDigestAuthorizationVary {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    private static final String REALM = "digest realm";
    private static final String NONCE = "0123456789abcdef";

    @RegisterExtension
    private final TestAsyncServerResources serverResources;
    @RegisterExtension
    private final CachingHttpAsyncClientResource clientResource;

    private final AtomicInteger resourceHits = new AtomicInteger();

    TestCachingAsyncClientDigestAuthorizationVary() throws Exception {
        this.serverResources = new TestAsyncServerResources(URIScheme.HTTP, TIMEOUT, ServerProtocolLevel.STANDARD);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("/resource", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) {
                        resourceHits.incrementAndGet();
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
                        response.setHeader(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);
                        response.setBody("anonymous content", ContentType.TEXT_PLAIN);
                        return response;
                    }

                })
                .register("/digest", () -> new AbstractSimpleServerExchangeHandler() {

                    @Override
                    protected SimpleHttpResponse handle(
                            final SimpleHttpRequest request,
                            final HttpCoreContext context) {
                        final Header authorization = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                        if (authorization != null && authorization.getValue().startsWith("Digest")) {
                            final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                            response.setBody("authenticated", ContentType.TEXT_PLAIN);
                            return response;
                        }
                        final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_UNAUTHORIZED);
                        response.addHeader(HttpHeaders.WWW_AUTHENTICATE,
                                "Digest realm=\"" + REALM + "\", nonce=\"" + NONCE + "\", qop=\"auth\", algorithm=MD5");
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

    @Test
    void testDigestAuthenticatedRequestIsNotServedTheAnonymousVariant() throws Exception {
        final HttpHost target = startServer();
        final CloseableHttpAsyncClient client = clientResource.client();

        final HttpCacheContext context = HttpCacheContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, new UsernamePasswordCredentials("alice", "secret".toCharArray()))
                .build());

        // Caches the anonymous Vary: Authorization variant for /resource.
        SimpleHttpResponse response = client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/resource")
                        .build(), context, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals(1, resourceHits.get());

        // Digest-authenticates this same context against a different URI on the same host: the
        // context now has an established, SUCCESS-state Digest AuthExchange for the target.
        response = client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/digest")
                        .build(), context, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());

        // The cache cannot safely preview what this Digest-authenticated request would send, so it
        // must not assume it matches the cached anonymous variant: this must reach the backend again,
        // proven by the origin hit count, not merely by the returned body.
        response = client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/resource")
                        .build(), context, null)
                .get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals(2, resourceHits.get());
    }

}
