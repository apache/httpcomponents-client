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
import java.nio.CharBuffer;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.AbstractCharResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

/**
 * Example of asynchronous HTTP/1.1 request execution with response streaming.
 */
public class AsyncClientHttpExchangeStreaming {

    public static void main(final String[] args) throws Exception {

        final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setSoTimeout(Timeout.ofSeconds(5))
                .build();

        final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setIOReactorConfig(ioReactorConfig)
                .build();

        client.start();

        final HttpHost target = new HttpHost("httpbin.org");
        final String[] requestUris = new String[] {"/", "/ip", "/user-agent", "/headers"};

        for (final String requestUri: requestUris) {
            final Future<Void> future = client.execute(
                    new BasicRequestProducer(Method.GET, target, requestUri),
                    new AbstractCharResponseConsumer<Void>() {

                        @Override
                        protected void start(
                                final HttpResponse response,
                                final ContentType contentType) throws HttpException, IOException {
                            System.out.println(requestUri + "->" + new StatusLine(response));
                        }

                        @Override
                        protected int capacityIncrement() {
                            return Integer.MAX_VALUE;
                        }

                        @Override
                        protected void data(final CharBuffer data, final boolean endOfStream) throws IOException {
                            while (data.hasRemaining()) {
                                System.out.print(data.get());
                            }
                            if (endOfStream) {
                                System.out.println();
                            }
                        }

                        @Override
                        protected Void buildResult() throws IOException {
                            return null;
                        }

                        @Override
                        public void failed(final Exception cause) {
                            System.out.println(requestUri + "->" + cause);
                        }

                        @Override
                        public void releaseResources() {
                        }

                    }, null);
            future.get();
        }

        System.out.println("Shutting down");
        client.close(CloseMode.GRACEFUL);
    }

}
