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

import org.apache.hc.client5.http.async.methods.DeflatingAsyncEntityProducer;
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
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.io.CloseMode;

/**
 * Simple command-line tool that sends a {@code deflate}-compressed JSON
 * payload to <a href="https://httpbin.org/post">httpbin.org/post</a> and
 * prints the echoed response.
 *
 * <p>The example demonstrates how to:</p>
 * <ol>
 *   <li>Wrap a regular {@link AsyncEntityProducer} with
 *       {@link DeflatingAsyncEntityProducer}.</li>
 *   <li>Add the mandatory {@code Content-Encoding} request header.</li>
 *   <li>Consume the JSON echo using {@link StringAsyncEntityConsumer}.</li>
 * </ol>
 *
 * <p>Swap {@code DeflatingAsyncEntityProducer} for your future
 * {@code GzipAsyncEntityProducer} once gzip support is added.</p>
 *
 * @since 5.6
 */
public final class AsyncClientDeflatePostExample {

    private AsyncClientDeflatePostExample() {
    }

    public static void main(final String[] args) throws Exception {

        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            /* ------------- original payload ------------------ */
            final String json = "{\"msg\":\"hello compressed world\"}";
            final AsyncEntityProducer raw = new StringAsyncEntityProducer(json, ContentType.APPLICATION_JSON);

            /* ------------- wrap with DEFLATE ------------------ */
            final AsyncEntityProducer deflated = new DeflatingAsyncEntityProducer(raw);

            /* ------------- build request ---------------------- */
            final SimpleHttpRequest req = SimpleRequestBuilder.post("https://httpbin.org/post")
                    .build();

            final Future<Message<HttpResponse, String>> f = client.execute(
                    new BasicRequestProducer(req, deflated),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    new FutureCallback<Message<HttpResponse, String>>() {

                        public void completed(final Message<HttpResponse, String> m) {
                            System.out.println(new StatusLine(m.getHead()));
                            System.out.println("\nEchoed JSON:\n" + m.getBody());
                        }

                        public void failed(final Exception ex) {
                            System.out.println(req + "->" + ex);
                        }

                        public void cancelled() {
                            System.err.println("cancelled");
                        }
                    });

            f.get();                       // wait
            client.close(CloseMode.GRACEFUL);
        }
    }
}
