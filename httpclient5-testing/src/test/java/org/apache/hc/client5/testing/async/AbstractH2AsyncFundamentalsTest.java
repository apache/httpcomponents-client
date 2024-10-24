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

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.Result;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.RequestNotExecutedException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.AsyncRequestConsumer;
import org.apache.hc.core5.http.nio.AsyncServerRequestHandler;
import org.apache.hc.core5.http.nio.entity.DiscardingEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.AbstractAsyncPushHandler;
import org.apache.hc.core5.http.nio.support.AbstractAsyncRequesterConsumer;
import org.apache.hc.core5.http.nio.support.AbstractServerExchangeHandler;
import org.apache.hc.core5.http.nio.support.BasicPushProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseProducer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http.support.BasicResponseBuilder;
import org.apache.hc.core5.http2.config.H2Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

abstract class AbstractH2AsyncFundamentalsTest extends AbstractHttpAsyncFundamentalsTest {

    public AbstractH2AsyncFundamentalsTest(final URIScheme scheme, final ClientProtocolLevel clientProtocolLevel, final ServerProtocolLevel serverProtocolLevel) {
        super(scheme, clientProtocolLevel, serverProtocolLevel);
    }

    @Test
    void testPush() throws Exception {
        configureServer(bootstrap -> bootstrap
                .register("/pushy", () -> new AbstractServerExchangeHandler<HttpRequest>() {

                    @Override
                    protected AsyncRequestConsumer<HttpRequest> supplyConsumer(
                            final HttpRequest request,
                            final EntityDetails entityDetails,
                            final HttpContext context) throws HttpException {

                        return new AbstractAsyncRequesterConsumer<HttpRequest, Void>(new DiscardingEntityConsumer<>()) {

                            @Override
                            protected HttpRequest buildResult(final HttpRequest request, final Void entity, final ContentType contentType) {
                                return request;
                            }

                        };
                    }

                    @Override
                    protected void handle(
                            final HttpRequest request,
                            final AsyncServerRequestHandler.ResponseTrigger responseTrigger,
                            final HttpContext context) throws HttpException, IOException {
                        responseTrigger.pushPromise(
                                BasicRequestBuilder.copy(request)
                                        .setPath("/aaa")
                                        .build(),
                                context,
                                new BasicPushProducer(BasicResponseBuilder.create(HttpStatus.SC_OK)
                                        .build(),
                                        new StringAsyncEntityProducer("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", ContentType.TEXT_PLAIN)));
                        responseTrigger.pushPromise(
                                BasicRequestBuilder.copy(request)
                                        .setPath("/bbb")
                                        .build(),
                                context,
                                new BasicPushProducer(
                                        BasicResponseBuilder.create(HttpStatus.SC_OK).build(),
                                        new StringAsyncEntityProducer("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", ContentType.TEXT_PLAIN)));
                        responseTrigger.submitResponse(
                                new BasicResponseProducer(
                                        BasicResponseBuilder.create(HttpStatus.SC_OK).build(),
                                        new StringAsyncEntityProducer("I am being very pushy")
                                ),
                                context);
                    }

                }));

        configureClient(builder -> builder
                .setH2Config(H2Config.custom()
                        .setPushEnabled(true)
                        .build()));

        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        client.start();

        final Queue<Result<String>> pushMessageQueue = new ConcurrentLinkedQueue<>();
        final CountDownLatch latch = new CountDownLatch(3);
        final HttpClientContext context = HttpClientContext.create();
        final SimpleHttpRequest request = SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/pushy")
                .build();
        client.execute(
                SimpleRequestProducer.create(request),
                SimpleResponseConsumer.create(),
                (r, c) -> new AbstractAsyncPushHandler<SimpleHttpResponse>(SimpleResponseConsumer.create()) {

                    @Override
                    protected void handleResponse(final HttpRequest promise,
                                                  final SimpleHttpResponse response) throws IOException, HttpException {
                        pushMessageQueue.add(new Result<>(promise, response, response.getBodyText()));
                        latch.countDown();
                    }

                    @Override
                    protected void handleError(final HttpRequest promise, final Exception cause) {
                        pushMessageQueue.add(new Result<>(promise, cause));
                        latch.countDown();
                    }

                },
                context,
                new FutureCallback<SimpleHttpResponse>() {

                    @Override
                    public void completed(final SimpleHttpResponse response) {
                        pushMessageQueue.add(new Result<>(request, response, response.getBodyText()));
                        latch.countDown();
                    }

                    @Override
                    public void failed(final Exception ex) {
                        pushMessageQueue.add(new Result<>(request, ex));
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        pushMessageQueue.add(new Result<>(request, new RequestNotExecutedException()));
                        latch.countDown();
                    }

                }
        );
        Assertions.assertTrue(latch.await(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit()));
        Assertions.assertEquals(3, pushMessageQueue.size());
        for (final Result<String> result : pushMessageQueue) {
            if (result.isOK()) {
                Assertions.assertEquals(HttpStatus.SC_OK, result.response.getCode());
                final String path = result.request.getPath();
                if (path.equals("/pushy")) {
                    Assertions.assertEquals("I am being very pushy", result.content);
                } else if (path.equals("/aaa")) {
                    Assertions.assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", result.content);
                } else if (path.equals("/bbb")) {
                    Assertions.assertEquals("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", result.content);
                } else {
                    Assertions.fail("Unxpected request path: " + path);
                }
            }
        }
    }

}