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

import org.apache.hc.client5.http.async.methods.DecompressingStringAsyncEntityConsumer;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.io.CloseMode;

/**
 * Example demonstrating how to plug {@link DecompressingStringAsyncEntityConsumer}
 * into the asynchronous request pipeline.  The program performs a
 * {@code GET https://httpbin.org/gzip} request, which returns a
 * {@code Content-Encoding: gzip} response.  The custom consumer transparently
 * decompresses the entity and supplies a plain {@link String} to the callback.
 *
 * <p>The built‑in {@code ContentCompressionExec} element already performs
 * transparent decompression when {@link CloseableHttpAsyncClient} is used with
 * default settings.  This example illustrates how to achieve the same result
 * when that feature is <em>disabled</em> (for instance to register extra codecs
 * at runtime).</p>
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Create a {@link DecompressingStringAsyncEntityConsumer}.</li>
 *   <li>Pass it directly to {@link BasicResponseConsumer}.</li>
 *   <li>Execute the request and read the decoded body in the callback.</li>
 * </ol>
 *
 * @since 5.6
 */
public final class AsyncClientDecompressionExample {

    private AsyncClientDecompressionExample() {
    }

    public static void main(final String[] args) throws Exception {
        /*
         * Disable the built‑in ContentCompressionExec so only the custom
         * consumer performs decompression.
         */
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .build()) {

            client.start();

            // Build request that explicitly asks for gzip
            final SimpleHttpRequest request = SimpleRequestBuilder.get("https://httpbin.org/gzip")
                    .addHeader("Accept-Encoding", "gzip")
                    .build();

            final Future<Message<HttpResponse, String>> future = client.execute(
                    SimpleRequestProducer.create(request),
                    new BasicResponseConsumer<>(new DecompressingStringAsyncEntityConsumer()),
                    new FutureCallback<Message<HttpResponse, String>>() {
                        @Override
                        public void completed(final Message<HttpResponse, String> msg) {
                            System.out.println(request + " -> " + new StatusLine(msg.getHead()));
                            System.out.println("\nDecoded body:\n" + msg.getBody());
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
