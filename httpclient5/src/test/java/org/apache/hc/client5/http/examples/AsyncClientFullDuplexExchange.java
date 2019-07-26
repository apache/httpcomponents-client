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
package org.apache.hc.client5.http.examples;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * This example demonstrates a full-duplex, streaming HTTP/1.1 message exchange.
 */
public class AsyncClientFullDuplexExchange {

    public static void main(final String[] args) throws Exception {

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(Timeout.ofSeconds(5))
                .build();

        final MinimalHttpAsyncClient client = HttpAsyncClients.createMinimal(
                HttpVersionPolicy.NEGOTIATE,
                H2Config.DEFAULT,
                Http1Config.DEFAULT,
                ioReactorConfig);

        client.start();

        final URI requestUri = new URI("http://httpbin.org/post");
        final BasicRequestProducer requestProducer = new BasicRequestProducer(
                "POST", requestUri, new BasicAsyncEntityProducer("stuff", ContentType.TEXT_PLAIN));
        final BasicResponseConsumer<String> responseConsumer = new BasicResponseConsumer<>(
                new StringAsyncEntityConsumer());

        final CountDownLatch latch = new CountDownLatch(1);
        client.execute(new AsyncClientExchangeHandler() {

            @Override
            public void releaseResources() {
                requestProducer.releaseResources();
                responseConsumer.releaseResources();
                latch.countDown();
            }

            @Override
            public void cancel() {
                System.out.println(requestUri + " cancelled");
            }

            @Override
            public void failed(final Exception cause) {
                System.out.println(requestUri + "->" + cause);
            }

            @Override
            public void produceRequest(final RequestChannel channel, final HttpContext context) throws HttpException, IOException {
                requestProducer.sendRequest(channel, context);
            }

            @Override
            public int available() {
                return requestProducer.available();
            }

            @Override
            public void produce(final DataStreamChannel channel) throws IOException {
                requestProducer.produce(channel);
            }

            @Override
            public void consumeInformation(
                    final HttpResponse response,
                    final HttpContext context) throws HttpException, IOException {
                System.out.println(requestUri + "->" + response.getCode());
            }

            @Override
            public void consumeResponse(
                    final HttpResponse response,
                    final EntityDetails entityDetails,
                    final HttpContext context) throws HttpException, IOException {
                System.out.println(requestUri + "->" + response.getCode());
                responseConsumer.consumeResponse(response, entityDetails, context, null);
            }

            @Override
            public void updateCapacity(final CapacityChannel capacityChannel) throws IOException {
                responseConsumer.updateCapacity(capacityChannel);
            }

            @Override
            public void consume(final ByteBuffer src) throws IOException {
                responseConsumer.consume(src);
            }

            @Override
            public void streamEnd(final List<? extends Header> trailers) throws HttpException, IOException {
                responseConsumer.streamEnd(trailers);
            }

        });
        latch.await(1, TimeUnit.MINUTES);

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
    }

}
