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

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.cache.HttpCacheContext;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.cache.HeapResourceFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.classic.AuthenticatingDecorator;
import org.apache.hc.client5.testing.extension.sync.CachingHttpClientResource;
import org.apache.hc.client5.testing.extension.sync.TestServerResources;
import org.apache.hc.core5.http.ContentType;
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
 * A shared cache must not serve a response to an authenticated request
 * to a different execution context.
 */
class TestCachingClientAuthentication {

    static final Timeout TIMEOUT = Timeout.ofMinutes(1);

    @RegisterExtension
    private final TestServerResources serverResources;
    @RegisterExtension
    private final CachingHttpClientResource clientResource;

    private final AtomicInteger originHits = new AtomicInteger();

    TestCachingClientAuthentication() throws Exception {
        this.serverResources = new TestServerResources(URIScheme.HTTP, TIMEOUT);
        this.serverResources.configureServer(bootstrap -> bootstrap
                .setExchangeHandlerDecorator(requestHandler -> new AuthenticatingDecorator(
                        requestHandler, new BasicTestAuthenticator("test:test", "test realm")))
                .register("*", (request, response, context) -> {
                    originHits.incrementAndGet();
                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=60");
                    response.setEntity(new StringEntity("private data", ContentType.TEXT_PLAIN));
                }));
        this.clientResource = new CachingHttpClientResource();
        this.clientResource.configure(builder -> builder
                .setResourceFactory(HeapResourceFactory.INSTANCE));
    }

    HttpHost startServer() throws Exception {
        final InetSocketAddress address = serverResources.server().start();
        return new HttpHost(serverResources.scheme().id, "localhost", address.getPort());
    }

    void assertAuthenticatedResponse(
            final CloseableHttpClient client,
            final HttpHost target,
            final HttpCacheContext context) throws Exception {
        client.execute(target, new HttpGet("/private"), context, response -> {
            Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
            Assertions.assertEquals("private data", EntityUtils.toString(response.getEntity()));
            return null;
        });
    }

    void assertNotServedToAnonymousContext(
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

}
