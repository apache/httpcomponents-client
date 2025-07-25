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

import org.apache.hc.client5.http.async.methods.InflatingAsyncEntityConsumer;
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
 * Shows how to plug {@link InflatingAsyncEntityConsumer} into the response
 * pipeline so that a {@code deflate}-encoded body is transparently inflated.
 *
 * <p>The example calls <a href="https://httpbin.org/deflate">httpbin</a>,
 * which deliberately returns a small compressed document.  The consumer
 * detects the zlib wrapper automatically and hands the caller a plain
 * UTF-8 string.</p>
 *
 * <p>Swap the inner consumer with your own implementation if you need to
 * stream the inflated bytes directly into a file or parser.</p>
 *
 * @since 5.6
 */
public class AsyncClientInflateDecompressionExample {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final SimpleHttpRequest request =
                    SimpleRequestBuilder.get("https://httpbin.org/deflate").build();

            final InflatingAsyncEntityConsumer<String> inflating =
                    new InflatingAsyncEntityConsumer<>(new StringAsyncEntityConsumer());

            final Future<Message<HttpResponse, String>> future = client.execute(
                    SimpleRequestProducer.create(request),
                    new BasicResponseConsumer<>(inflating),
                    new FutureCallback<Message<HttpResponse, String>>() {

                        @Override
                        public void completed(final Message<HttpResponse, String> result) {
                            System.out.println(request.getRequestUri() + " -> " + result.getHead().getCode());
                            System.out.println("Decompressed body:\n" + result.getBody());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            ex.printStackTrace();
                        }

                        @Override
                        public void cancelled() {
                            System.out.println("cancelled");
                        }
                    });

            future.get();
        }
    }
}
