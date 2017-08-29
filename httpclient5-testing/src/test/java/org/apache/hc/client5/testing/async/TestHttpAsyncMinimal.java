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
package org.apache.hc.client5.testing.async;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.async.methods.AsyncRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.H2TlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.nio.BasicResponseConsumer;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.ShutdownType;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.reactor.ListenerEndpoint;
import org.apache.hc.core5.testing.nio.Http2TestServer;
import org.apache.hc.core5.util.TimeValue;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestHttpAsyncMinimal {

    @Parameterized.Parameters(name = "{0} {1}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { HttpVersion.HTTP_1_1, URIScheme.HTTP },
                { HttpVersion.HTTP_1_1, URIScheme.HTTPS },
                { HttpVersion.HTTP_2, URIScheme.HTTP },
                { HttpVersion.HTTP_2, URIScheme.HTTPS }
        });
    }

    protected final HttpVersion version;
    protected final URIScheme scheme;

    public TestHttpAsyncMinimal(final HttpVersion version, final URIScheme scheme) {
        this.version = version;
        this.scheme = scheme;
    }

    protected Http2TestServer server;
    protected MinimalHttpAsyncClient httpclient;

    @Rule
    public ExternalResource serverResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            server = new Http2TestServer(
                    IOReactorConfig.DEFAULT,
                    scheme == URIScheme.HTTPS ? SSLTestContexts.createServerSSLContext() : null);
            server.register("/echo/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    return new AsyncEchoHandler();
                }

            });
            server.register("/random/*", new Supplier<AsyncServerExchangeHandler>() {

                @Override
                public AsyncServerExchangeHandler get() {
                    return new AsyncRandomHandler();
                }

            });
        }

        @Override
        protected void after() {
            if (server != null) {
                server.shutdown(TimeValue.ofSeconds(5));
                server = null;
            }
        }

    };

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            final PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
                    .setTlsStrategy(new H2TlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .build();
            final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                    .setSoTimeout(5, TimeUnit.SECONDS)
                    .build();
            if (version.greaterEquals(HttpVersion.HTTP_2)) {
                httpclient = HttpAsyncClients.createMinimal(
                        HttpVersionPolicy.FORCE_HTTP_2, H2Config.DEFAULT, H1Config.DEFAULT, ioReactorConfig, connectionManager);
            } else {
                httpclient = HttpAsyncClients.createMinimal(
                        HttpVersionPolicy.FORCE_HTTP_1, H2Config.DEFAULT, H1Config.DEFAULT, ioReactorConfig, connectionManager);
            }
        }

        @Override
        protected void after() {
            if (httpclient != null) {
                httpclient.shutdown(ShutdownType.GRACEFUL);
                httpclient = null;
            }
        }

    };

    public HttpHost start() throws Exception {
        if (version.greaterEquals(HttpVersion.HTTP_2)) {
            server.start(H2Config.DEFAULT);
        } else {
            server.start(H1Config.DEFAULT);
        }
        final Future<ListenerEndpoint> endpointFuture = server.listen(new InetSocketAddress(0));
        httpclient.start();
        final ListenerEndpoint endpoint = endpointFuture.get();
        final InetSocketAddress address = (InetSocketAddress) endpoint.getAddress();
        return new HttpHost("localhost", address.getPort(), scheme.name());
    }

    @Test
    public void testSequenctialGetRequests() throws Exception {
        final HttpHost target = start();
        for (int i = 0; i < 3; i++) {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequest.get(target, "/random/2048"), null);
            final SimpleHttpResponse response = future.get();
            Assert.assertThat(response, CoreMatchers.notNullValue());
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final String body = response.getBody();
            Assert.assertThat(body, CoreMatchers.notNullValue());
            Assert.assertThat(body.length(), CoreMatchers.equalTo(2048));
        }
    }

    @Test
    public void testSequenctialHeadRequests() throws Exception {
        final HttpHost target = start();
        for (int i = 0; i < 3; i++) {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleHttpRequest.head(target, "/random/2048"), null);
            final SimpleHttpResponse response = future.get();
            Assert.assertThat(response, CoreMatchers.notNullValue());
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final String body = response.getBody();
            Assert.assertThat(body, CoreMatchers.nullValue());
        }
    }

    @Test
    public void testConcurrentPostsOverMultipleConnections() throws Exception {
        final HttpHost target = start();
        final byte[] b1 = new byte[1024];
        final Random rnd = new Random(System.currentTimeMillis());
        rnd.nextBytes(b1);

        final int reqCount = 20;

        final Queue<Future<Message<HttpResponse, byte[]>>> queue = new LinkedList<>();
        for (int i = 0; i < reqCount; i++) {
            final Future<Message<HttpResponse, byte[]>> future = httpclient.execute(
                    AsyncRequestBuilder.post(target, "/echo/")
                            .setEntity(b1, ContentType.APPLICATION_OCTET_STREAM)
                            .build(),
                    new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()), HttpClientContext.create(), null);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, byte[]>> future = queue.remove();
            final Message<HttpResponse, byte[]> responseMessage = future.get();
            Assert.assertThat(responseMessage, CoreMatchers.notNullValue());
            final HttpResponse response = responseMessage.getHead();
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final byte[] b2 = responseMessage.getBody();
            Assert.assertThat(b1, CoreMatchers.equalTo(b2));
        }
    }

    @Test
    public void testConcurrentPostsOverSingleConnection() throws Exception {
        final HttpHost target = start();
        final byte[] b1 = new byte[1024];
        final Random rnd = new Random(System.currentTimeMillis());
        rnd.nextBytes(b1);

        final int reqCount = 20;

        final Future<AsyncClientEndpoint> endpointLease = httpclient.lease(target, null);
        final AsyncClientEndpoint endpoint = endpointLease.get(5, TimeUnit.SECONDS);
        try {
            final Queue<Future<Message<HttpResponse, byte[]>>> queue = new LinkedList<>();
            for (int i = 0; i < reqCount; i++) {
                final Future<Message<HttpResponse, byte[]>> future = endpoint.execute(
                        AsyncRequestBuilder.post(target, "/echo/")
                                .setEntity(b1, ContentType.APPLICATION_OCTET_STREAM)
                                .build(),
                        new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()), HttpClientContext.create(), null);
                queue.add(future);
            }
            while (!queue.isEmpty()) {
                final Future<Message<HttpResponse, byte[]>> future = queue.remove();
                final Message<HttpResponse, byte[]> responseMessage = future.get();
                Assert.assertThat(responseMessage, CoreMatchers.notNullValue());
                final HttpResponse response = responseMessage.getHead();
                Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
                final byte[] b2 = responseMessage.getBody();
                Assert.assertThat(b1, CoreMatchers.equalTo(b2));
                endpoint.releaseAndReuse();
            }
        } finally {
            endpoint.releaseAndDiscard();
        }

    }

}