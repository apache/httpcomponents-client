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

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactive.ReactiveEntityProducer;
import org.apache.hc.core5.reactive.ReactiveResponseConsumer;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.reactivestreams.Publisher;

import io.reactivex.Flowable;
import io.reactivex.Notification;
import io.reactivex.Observable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

/**
 * This example demonstrates a reactive, full-duplex HTTP/1.1 message exchange using RxJava.
 */
public class ReactiveClientFullDuplexExchange {

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
        final byte[] bs = "stuff".getBytes(StandardCharsets.UTF_8);
        final ReactiveEntityProducer reactiveEntityProducer = new ReactiveEntityProducer(
            Flowable.just(ByteBuffer.wrap(bs)), bs.length, ContentType.TEXT_PLAIN, null);
        final BasicRequestProducer requestProducer = new BasicRequestProducer(
                "POST", requestUri, reactiveEntityProducer);

        final ReactiveResponseConsumer consumer = new ReactiveResponseConsumer();
        final Future<Void> requestFuture = client.execute(requestProducer, consumer, null);
        final Message<HttpResponse, Publisher<ByteBuffer>> streamingResponse = consumer.getResponseFuture().get();

        System.out.println(streamingResponse.getHead());
        for (final Header header : streamingResponse.getHead().getHeaders()) {
            System.out.println(header.toString());
        }
        System.out.println();

        Observable.fromPublisher(streamingResponse.getBody())
            .map(new Function<ByteBuffer, String>() {
                @Override
                public String apply(final ByteBuffer byteBuffer) throws Exception {
                    final byte[] string = new byte[byteBuffer.remaining()];
                    byteBuffer.get(string);
                    return new String(string);
                }
            })
            .materialize()
            .forEach(new Consumer<Notification<String>>() {
                @Override
                public void accept(final Notification<String> byteBufferNotification) throws Exception {
                    System.out.println(byteBufferNotification.toString());
                }
            });

        requestFuture.get(1, TimeUnit.MINUTES);

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
    }
}
