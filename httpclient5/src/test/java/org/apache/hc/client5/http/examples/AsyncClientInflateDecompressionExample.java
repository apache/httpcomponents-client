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
 * Demonstrates <b>transparent response decompression</b> added in
 * HttpClient&nbsp;5.6.  The async builder inserts
 * {@code ContentCompressionAsyncExec}, which
 * <em>automatically</em> wraps whatever
 * {@link org.apache.hc.core5.http.nio.AsyncDataConsumer} the user provides
 * with an {@code InflatingAsyncDataConsumer} whenever the server sends
 * {@code Content-Encoding: deflate}.
 *
 * <p>In the DEFLATE algorithm the server <em>deflates</em> (compresses)
 * the payload; the client then <em>inflates</em> (decompresses) it.
 * The example calls <a href="https://httpbin.org/deflate">https://httpbin.org/deflate</a>,
 * whose response is deliberately compressed.  Because decompression is now
 * part of the execution pipeline, the inner
 * {@link org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer}
 * receives plain UTF-8 text and the program prints the readable JSON.</p>
 *
 * <p>If you need to stream the inflated bytes into a file or a custom parser,
 * just replace the {@code StringAsyncEntityConsumer} with your own
 * {@code AsyncDataConsumer}; no further changes are required.</p>
 *
 * @since 5.6
 */

public class AsyncClientInflateDecompressionExample {

    public static void main(final String[] args) throws Exception {

        /* The default builder now contains ContentCompressionAsyncExec,
           so transparent decompression is automatic. */
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final SimpleHttpRequest request =
                    SimpleRequestBuilder.get("https://httpbin.org/deflate").build();

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
