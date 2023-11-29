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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.AbstractBinPushConsumer;
import org.apache.hc.client5.http.async.methods.AbstractCharResponseConsumer;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.io.CloseMode;

/**
 * This example demonstrates handling of HTTP/2 message exchanges pushed by the server.
 */
public class AsyncClientH2ServerPush {

    public static void main(final String[] args) throws Exception {

        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setH2Config(H2Config.custom()
                        .setPushEnabled(true)
                        .build())
                .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                        .setDefaultTlsConfig(TlsConfig.custom()
                                .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                                .build())
                        .build())
                .build();

        client.start();

        client.register("*", () -> new AbstractBinPushConsumer() {

            @Override
            protected void start(
                    final HttpRequest promise,
                    final HttpResponse response,
                    final ContentType contentType) throws HttpException, IOException {
                System.out.println(promise.getPath() + " (push)->" + new StatusLine(response));
            }

            @Override
            protected int capacityIncrement() {
                return Integer.MAX_VALUE;
            }

            @Override
            protected void data(final ByteBuffer data, final boolean endOfStream) throws IOException {
            }

            @Override
            protected void completed() {
            }

            @Override
            public void failed(final Exception cause) {
                System.out.println("(push)->" + cause);
            }

            @Override
            public void releaseResources() {
            }

        });

        final BasicHttpRequest request = BasicRequestBuilder.get("https://nghttp2.org/httpbin/").build();

        System.out.println("Executing request " + request);
        final Future<Void> future = client.execute(
                new BasicRequestProducer(request, null),
                new AbstractCharResponseConsumer<Void>() {

                    @Override
                    protected void start(
                            final HttpResponse response,
                            final ContentType contentType) throws HttpException, IOException {
                        System.out.println(request + "->" + new StatusLine(response));
                    }

                    @Override
                    protected int capacityIncrement() {
                        return Integer.MAX_VALUE;
                    }

                    @Override
                    protected void data(final CharBuffer data, final boolean endOfStream) throws IOException {
                    }

                    @Override
                    protected Void buildResult() throws IOException {
                        return null;
                    }

                    @Override
                    public void failed(final Exception cause) {
                        System.out.println(request + "->" + cause);
                    }

                    @Override
                    public void releaseResources() {
                    }

                }, null);
        future.get();

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
    }

}
