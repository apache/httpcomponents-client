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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.ByteBuffer;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
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
import org.apache.hc.core5.reactive.ReactiveServerExchangeHandler;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.apache.hc.core5.testing.reactive.ReactiveRandomProcessor;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;

public abstract class TestHttp1Reactive extends AbstractHttpReactiveFundamentalsTest<CloseableHttpAsyncClient> {

    public TestHttp1Reactive(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected H2TestServer startServer() throws Exception {
        return startServer(Http1Config.DEFAULT, null, null);
    }

    @Override
    protected CloseableHttpAsyncClient startClient() throws Exception {
        return startClient(b -> {});
    }

    @ParameterizedTest(name = "{displayName}; concurrent connections: {0}")
    @ValueSource(ints = {5, 1, 20})
    @Timeout(value = 60_000, unit = MILLISECONDS)
    public void testSequentialGetRequestsCloseConnection(final int concurrentConns) throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveRandomProcessor()));
        final HttpHost target = targetHost();

        final CloseableHttpAsyncClient client = startClient();
        final PoolingAsyncClientConnectionManager connManager = connManager();
        connManager.setDefaultMaxPerRoute(concurrentConns);
        connManager.setMaxTotal(100);

        for (int i = 0; i < 3; i++) {
            final SimpleHttpRequest get = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath("/random/2048")
                    .build();
            get.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
            final AsyncRequestProducer request = AsyncRequestBuilder.get(target + "/random/2048").build();
            final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();

            client.execute(request, consumer, null);

            final Message<HttpResponse, Publisher<ByteBuffer>> response = consumer.getResponseFuture().get();
            assertThat(response, CoreMatchers.notNullValue());
            assertThat(response.getHead().getCode(), CoreMatchers.equalTo(200));
            final String body = publisherToString(response.getBody());
            assertThat(body, CoreMatchers.notNullValue());
            assertThat(body.length(), CoreMatchers.equalTo(2048));
        }
    }

    @Test
    @Timeout(value = 60_000, unit = MILLISECONDS)
    public void testSharedPool() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveRandomProcessor()));
        final HttpHost target = targetHost();

        final CloseableHttpAsyncClient client = startClient();
        final PoolingAsyncClientConnectionManager connManager = connManager();

        final AsyncRequestProducer request1 = AsyncRequestBuilder.get(target + "/random/2048").build();
        final ReactiveResponseConsumer consumer1 = new ReactiveResponseConsumer();

        client.execute(request1, consumer1, null);

        final Message<HttpResponse, Publisher<ByteBuffer>> response1 = consumer1.getResponseFuture().get();
        assertThat(response1, CoreMatchers.notNullValue());
        assertThat(response1.getHead(), CoreMatchers.notNullValue());
        assertThat(response1.getHead().getCode(), CoreMatchers.equalTo(200));
        final String body1 = publisherToString(response1.getBody());
        assertThat(body1, CoreMatchers.notNullValue());
        assertThat(body1.length(), CoreMatchers.equalTo(2048));


        try (final CloseableHttpAsyncClient httpclient2 = HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .setConnectionManagerShared(true)
                .build()) {
            httpclient2.start();
            final AsyncRequestProducer request2 = AsyncRequestBuilder.get(target + "/random/2048").build();
            final ReactiveResponseConsumer consumer2 = new ReactiveResponseConsumer();

            httpclient2.execute(request2, consumer2, null);

            final Message<HttpResponse, Publisher<ByteBuffer>> response2 = consumer2.getResponseFuture().get();
            assertThat(response2, CoreMatchers.notNullValue());
            assertThat(response2.getHead().getCode(), CoreMatchers.equalTo(200));
            final String body2 = publisherToString(response2.getBody());
            assertThat(body2, CoreMatchers.notNullValue());
            assertThat(body2.length(), CoreMatchers.equalTo(2048));
        }

        final AsyncRequestProducer request3 = AsyncRequestBuilder.get(target + "/random/2048").build();
        final ReactiveResponseConsumer consumer3 = new ReactiveResponseConsumer();

        client.execute(request3, consumer3, null);

        final Message<HttpResponse, Publisher<ByteBuffer>> response3 = consumer3.getResponseFuture().get();
        assertThat(response3, CoreMatchers.notNullValue());
        assertThat(response3.getHead().getCode(), CoreMatchers.equalTo(200));
        final String body3 = publisherToString(response3.getBody());
        assertThat(body3, CoreMatchers.notNullValue());
        assertThat(body3.length(), CoreMatchers.equalTo(2048));
    }

}
