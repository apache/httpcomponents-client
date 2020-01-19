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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.reactive.ReactiveResponseConsumer;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.reactivestreams.Publisher;

@RunWith(Parameterized.class)
public class TestHttp1Reactive extends AbstractHttpReactiveFundamentalsTest<CloseableHttpAsyncClient> {

    @Parameterized.Parameters(name = "HTTP/1.1 {0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { URIScheme.HTTP },
                { URIScheme.HTTPS },
        });
    }

    protected HttpAsyncClientBuilder clientBuilder;
    protected PoolingAsyncClientConnectionManager connManager;

    @Rule
    public ExternalResource connManagerResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                    .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .build();
        }

        @Override
        protected void after() {
            if (connManager != null) {
                connManager.close();
                connManager = null;
            }
        }

    };

    @Rule
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            clientBuilder = HttpAsyncClientBuilder.create()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(TIMEOUT)
                            .setConnectTimeout(TIMEOUT)
                            .build())
                    .setConnectionManager(connManager);
        }

    };

    public TestHttp1Reactive(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected CloseableHttpAsyncClient createClient() {
        return clientBuilder.build();
    }

    @Override
    public HttpHost start() throws Exception {
        return super.start(null, Http1Config.DEFAULT);
    }

    @Test(timeout = 60_000)
    public void testSequentialGetRequestsCloseConnection() throws Exception {
        final HttpHost target = start();
        for (int i = 0; i < 3; i++) {
            final SimpleHttpRequest get = SimpleHttpRequests.get(target, "/random/2048");
            get.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
            final AsyncRequestProducer request = AsyncRequestBuilder.get(target + "/random/2048").build();
            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();

            httpclient.execute(request, consumer, null);

            final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture().get();
            Assert.assertThat(response, CoreMatchers.notNullValue());
            Assert.assertThat(response.getHead().getCode(), CoreMatchers.equalTo(200));
            final String body = publisherToString(response.getBody());
            Assert.assertThat(body, CoreMatchers.notNullValue());
            Assert.assertThat(body.length(), CoreMatchers.equalTo(2048));
        }
    }

    @Test(timeout = 60_000)
    public void testConcurrentPostsOverMultipleConnections() throws Exception {
        connManager.setDefaultMaxPerRoute(20);
        connManager.setMaxTotal(100);
        super.testConcurrentPostRequests();
    }

    @Test(timeout = 60_000)
    public void testConcurrentPostsOverSingleConnection() throws Exception {
        connManager.setDefaultMaxPerRoute(1);
        connManager.setMaxTotal(100);
        super.testConcurrentPostRequests();
    }

    @Test(timeout = 60_000)
    public void testSharedPool() throws Exception {
        final HttpHost target = start();
        final AsyncRequestProducer request1 = AsyncRequestBuilder.get(target + "/random/2048").build();
        final ReactiveResponseConsumer consumer1 = new ReactiveResponseConsumer();

        httpclient.execute(request1, consumer1, null);

        final Message<HttpResponse, Publisher<ByteBuffer>> response1 = consumer1.getResponseFuture().get();
        Assert.assertThat(response1, CoreMatchers.notNullValue());
        Assert.assertThat(response1.getHead(), CoreMatchers.notNullValue());
        Assert.assertThat(response1.getHead().getCode(), CoreMatchers.equalTo(200));
        final String body1 = publisherToString(response1.getBody());
        Assert.assertThat(body1, CoreMatchers.notNullValue());
        Assert.assertThat(body1.length(), CoreMatchers.equalTo(2048));


        try (final CloseableHttpAsyncClient httpclient2 = HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .setConnectionManagerShared(true)
                .build()) {
            httpclient2.start();
            final AsyncRequestProducer request2 = AsyncRequestBuilder.get(target + "/random/2048").build();
            final ReactiveResponseConsumer consumer2 = new ReactiveResponseConsumer();

            httpclient2.execute(request2, consumer2, null);

            final Message<HttpResponse, Publisher<ByteBuffer>> response2 = consumer2.getResponseFuture().get();
            Assert.assertThat(response2, CoreMatchers.notNullValue());
            Assert.assertThat(response2.getHead().getCode(), CoreMatchers.equalTo(200));
            final String body2 = publisherToString(response2.getBody());
            Assert.assertThat(body2, CoreMatchers.notNullValue());
            Assert.assertThat(body2.length(), CoreMatchers.equalTo(2048));
        }

        final AsyncRequestProducer request3 = AsyncRequestBuilder.get(target + "/random/2048").build();
        final ReactiveResponseConsumer consumer3 = new ReactiveResponseConsumer();

        httpclient.execute(request3, consumer3, null);

        final Message<HttpResponse, Publisher<ByteBuffer>> response3 = consumer3.getResponseFuture().get();
        Assert.assertThat(response3, CoreMatchers.notNullValue());
        Assert.assertThat(response3.getHead().getCode(), CoreMatchers.equalTo(200));
        final String body3 = publisherToString(response3.getBody());
        Assert.assertThat(body3, CoreMatchers.notNullValue());
        Assert.assertThat(body3.length(), CoreMatchers.equalTo(2048));
    }

}
