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
import java.nio.ByteBuffer;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.AsyncRequestBuilder;
import org.apache.hc.core5.reactive.ReactiveResponseConsumer;
import org.apache.hc.core5.reactive.ReactiveServerExchangeHandler;
import org.apache.hc.core5.testing.reactive.ReactiveRandomProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;

abstract class TestHttp1Reactive extends AbstractHttpReactiveFundamentalsTest {

    public TestHttp1Reactive(final URIScheme scheme) {
        super(scheme, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD);
    }

    @ParameterizedTest(name = "{displayName}; concurrent connections: {0}")
    @ValueSource(ints = {5, 1, 20})
    @Timeout(value = 60_000, unit = MILLISECONDS)
    public void testSequentialGetRequestsCloseConnection(final int concurrentConns) throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveRandomProcessor())));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
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
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getHead().getCode());
            final String body = publisherToString(response.getBody());
            Assertions.assertNotNull(body);
            Assertions.assertEquals(2048, body.length());
        }
    }

    @Test
    @Timeout(value = 60_000, unit = MILLISECONDS)
    void testSharedPool() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", () ->
                new ReactiveServerExchangeHandler(new ReactiveRandomProcessor())));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();

        final AsyncRequestProducer request1 = AsyncRequestBuilder.get(target + "/random/2048").build();
        final ReactiveResponseConsumer consumer1 = new ReactiveResponseConsumer();

        client.execute(request1, consumer1, null);

        final Message<HttpResponse, Publisher<ByteBuffer>> response1 = consumer1.getResponseFuture().get();
        Assertions.assertNotNull(response1);
        Assertions.assertNotNull(response1.getHead());
        Assertions.assertEquals(200, response1.getHead().getCode());
        final String body1 = publisherToString(response1.getBody());
        Assertions.assertNotNull(body1);
        Assertions.assertEquals(2048, body1.length());


        try (final CloseableHttpAsyncClient httpclient2 = HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .setConnectionManagerShared(true)
                .build()) {
            httpclient2.start();
            final AsyncRequestProducer request2 = AsyncRequestBuilder.get(target + "/random/2048").build();
            final ReactiveResponseConsumer consumer2 = new ReactiveResponseConsumer();

            httpclient2.execute(request2, consumer2, null);

            final Message<HttpResponse, Publisher<ByteBuffer>> response2 = consumer2.getResponseFuture().get();
            Assertions.assertNotNull(response2);
            Assertions.assertEquals(200, response2.getHead().getCode());
            final String body2 = publisherToString(response2.getBody());
            Assertions.assertNotNull(body2);
            Assertions.assertEquals(2048, body2.length());
        }

        final AsyncRequestProducer request3 = AsyncRequestBuilder.get(target + "/random/2048").build();
        final ReactiveResponseConsumer consumer3 = new ReactiveResponseConsumer();

        client.execute(request3, consumer3, null);

        final Message<HttpResponse, Publisher<ByteBuffer>> response3 = consumer3.getResponseFuture().get();
        Assertions.assertNotNull(response3);
        Assertions.assertEquals(200, response3.getHead().getCode());
        final String body3 = publisherToString(response3.getBody());
        Assertions.assertNotNull(body3);
        Assertions.assertEquals(2048, body3.length());
    }

}
