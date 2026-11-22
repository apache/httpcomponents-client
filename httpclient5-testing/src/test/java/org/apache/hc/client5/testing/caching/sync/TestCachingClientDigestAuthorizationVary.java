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

import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
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
 * {@code Digest} is not {@code @AuthStateCacheable}: previewing what a Digest-authenticated request
 * would send would pop and advance its nonce count as a side effect of a mere cache lookup, which is
 * never safe. A request from a context that has already Digest-authenticated to the target host must
 * therefore never be served a {@code Vary: Authorization} variant that was cached for an anonymous
 * request; the cache cannot tell whether the two match, so it must go to the backend instead.
 */
class TestCachingClientDigestAuthorizationVary {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    private static final String REALM = "digest realm";
    private static final String NONCE = "0123456789abcdef";

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger resourceHits = new AtomicInteger();

    TestCachingClientDigestAuthorizationVary() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .register("/resource", (request, response, context) -> {
                    resourceHits.incrementAndGet();
                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
                    response.setHeader(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);
                    response.setEntity(new StringEntity("anonymous content", ContentType.TEXT_PLAIN));
                })
                .register("/digest", (request, response, context) -> {
                    final Header authorization = request.getFirstHeader(HttpHeaders.AUTHORIZATION);
                    if (authorization != null && authorization.getValue().startsWith("Digest")) {
                        response.setCode(HttpStatus.SC_OK);
                        response.setEntity(new StringEntity("authenticated", ContentType.TEXT_PLAIN));
                    } else {
                        response.setCode(HttpStatus.SC_UNAUTHORIZED);
                        response.addHeader(HttpHeaders.WWW_AUTHENTICATE,
                                "Digest realm=\"" + REALM + "\", nonce=\"" + NONCE + "\", qop=\"auth\", algorithm=MD5");
                    }
                }));
        this.clientResource = new CachingHttpClientResource();
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
        final CloseableHttpClient client = clientResource.client();

        final HttpCacheContext context = HttpCacheContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, new UsernamePasswordCredentials("alice", "secret".toCharArray()))
                .build());

        // Caches the anonymous Vary: Authorization variant for /resource.
        client.execute(target, new HttpGet("/resource"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity());
        });
        Assertions.assertEquals(1, resourceHits.get());

        // Digest-authenticates this same context against a different URI on the same host: the
        // context now has an established, SUCCESS-state Digest AuthExchange for the target.
        client.execute(target, new HttpGet("/digest"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            EntityUtils.consume(response.getEntity());
            return null;
        });

        // The cache cannot safely preview what this Digest-authenticated request would send, so it
        // must not assume it matches the cached anonymous variant: this must reach the backend again,
        // proven by the origin hit count, not merely by the returned body.
        client.execute(target, new HttpGet("/resource"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            return EntityUtils.toString(response.getEntity());
        });
        Assertions.assertEquals(2, resourceHits.get());
    }

}
