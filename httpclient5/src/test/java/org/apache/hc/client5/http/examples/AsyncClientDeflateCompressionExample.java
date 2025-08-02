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
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;

/**
 * Demonstrates how to POST a JSON body compressed on-the-fly with
 * {@link DeflatingAsyncEntityProducer}.  The program talks to
 * <a href="https://httpbin.org/post">httpbin</a>, which echoes your request,
 * making it easy to verify that the {@code Content-Encoding: deflate}
 * header was honoured.
 *
 * <p>Key take-aways:</p>
 * <ol>
 *   <li>Wrap any existing {@code AsyncEntityProducer} in the compressor.</li>
 *   <li>The {@code Content-Encoding} header is added automatically by HttpClient if not set in the request.</li>
 *   <li>The producer is streaming: huge payloads are never fully buffered.</li>
 * </ol>
 *
 * @since 5.6
 */
public class AsyncClientDeflateCompressionExample {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final String json = "{\"msg\":\"hello deflated world\"}";
            final AsyncEntityProducer raw =
                    new StringAsyncEntityProducer(json, ContentType.APPLICATION_JSON);

            final AsyncEntityProducer deflated = new DeflatingAsyncEntityProducer(raw);

            final SimpleHttpRequest request = SimpleRequestBuilder
                    .post("https://httpbin.org/post")
                    .build();

            final Future<Message<HttpResponse, String>> future = client.execute(
                    /* works in every 5.x version ↓ */
                    new BasicRequestProducer(request, deflated),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> result) {
                            System.out.println("HTTP " + result.getHead().getCode());
                            System.out.println(result.getBody());
                        }

                        @Override
                        public void failed(final Exception cause) {
                            System.out.println(request + "->" + cause);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(request + " cancelled");
                        }
                    });


            future.get();
        }
    }
}
