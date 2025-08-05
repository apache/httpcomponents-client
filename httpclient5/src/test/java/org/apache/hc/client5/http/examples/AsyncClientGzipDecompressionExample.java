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

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;

/**
 * <h2>Example – transparent <b>GZIP</b> <i>de-compression</i> with the async API</h2>
 *
 * <p>{@code ContentCompressionAsyncExec} is included by default in
 * {@link HttpAsyncClients#createDefault()}.
 * The interceptor adds the header
 * {@code Accept-Encoding: gzip, deflate} and automatically wraps the
 * downstream consumer in a {@code InflatingGzipDataConsumer} when the
 * server replies with {@code Content-Encoding: gzip}.</p>
 *
 * <p>The example performs a single {@code GET https://httpbin.org/gzip}
 * request — <a href="https://httpbin.org/#/Gzip/gzip_get">httpbin’s
 * endpoint</a> always returns a small GZIP-compressed JSON document.
 * The body is delivered to the caller as a plain UTF-8 string without
 * any additional code.</p>
 *
 * <p>Run it from a {@code main(...)} method; output is written to
 * {@code stdout}.</p>
 *
 * @since 5.6
 */
public final class AsyncClientGzipDecompressionExample {

    public static void main(final String[] args) throws Exception {

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final SimpleHttpRequest request =
                    SimpleRequestBuilder.get("https://httpbin.org/gzip").build();

            final Future<Message<HttpResponse, String>> future = client.execute(
                    SimpleRequestProducer.create(request),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> result) {
                            System.out.println(request.getRequestUri()
                                    + " -> " + result.getHead().getCode());
                            System.out.println("Decompressed body:\n" + result.getBody());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.err.println(request + "->" + ex);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(request + " cancelled");
                        }
                    });

            future.get();   // wait for completion
        }
    }
}
