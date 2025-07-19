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

import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.CompressingAsyncEntityProducer;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.io.CloseMode;

/**
 * Example client that shows how to send a <em>gzip‑compressed</em> POST body
 * using {@link CompressingAsyncEntityProducer}.  The program posts to
 * {@code https://httpbin.org/post}, which echoes request details back in the
 * JSON response so you can verify {@code Content-Encoding: gzip} was honoured.
 *
 * <p>The built‑in HttpClient pipeline has no automatic <strong>request‑side</strong>
 * compression; therefore you wrap the original producer with the generic
 * compressor and add the {@code Content-Encoding} header yourself.</p>
 *
 * @since 5.6
 */
public final class AsyncClientCompressionExample {

    private AsyncClientCompressionExample() {
    }

    public static void main(final String[] args) throws Exception {

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            // Raw payload and original producer
            final String payload = "{\"msg\":\"hello compressed world\"}";
            final AsyncEntityProducer rawProducer = new StringAsyncEntityProducer(payload);
            // Explicit content‑type because 1‑arg constructor cannot infer it
            // when we later bypass SimpleRequestProducer.create()


            // Wrap with gzip compression
            final AsyncEntityProducer gzipProducer = new CompressingAsyncEntityProducer(rawProducer, "gzip");

            // Build POST request and explicitly signal the encoding
            final SimpleHttpRequest request = SimpleRequestBuilder.post("https://httpbin.org/post")
                    .addHeader("Content-Encoding", "gzip")
                    .addHeader("Content-Type", ContentType.APPLICATION_JSON.toString())
                    .build();

            final Future<Message<HttpResponse, String>> future = client.execute(
                    new org.apache.hc.core5.http.nio.support.BasicRequestProducer(request, gzipProducer),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    new FutureCallback<Message<HttpResponse, String>>() {
                        @Override
                        public void completed(final Message<HttpResponse, String> msg) {
                            System.out.println(new StatusLine(msg.getHead()));
                            System.out.println("\nEchoed JSON:\n" + msg.getBody());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            ex.printStackTrace(System.err);
                        }

                        @Override
                        public void cancelled() {
                            System.err.println("Request cancelled");
                        }
                    });

            future.get();
            client.close(CloseMode.GRACEFUL);
        }
    }
}
