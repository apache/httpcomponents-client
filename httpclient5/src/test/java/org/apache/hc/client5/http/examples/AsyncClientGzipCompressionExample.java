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

import org.apache.hc.client5.http.async.methods.GzipCompressingAsyncEntityProducer;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;

/**
 * Example demonstrating how to use GzipCompressingAsyncEntityProducer for streaming GZIP-compressed uploads.
 */
public class AsyncClientGzipCompressionExample {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final String requestBody = "This is some text to be compressed and uploaded. Repeat for larger size if needed.";

            // Create a delegate producer for the raw content
            final StringAsyncEntityProducer delegate = new StringAsyncEntityProducer(requestBody, ContentType.TEXT_PLAIN);

            // Wrap with compressing producer
            final GzipCompressingAsyncEntityProducer compressingProducer = new GzipCompressingAsyncEntityProducer(delegate);

            final SimpleHttpRequest request = SimpleRequestBuilder.post("http://httpbin.org/post").build();

            final Future<SimpleHttpResponse> future = client.execute(
                    new BasicRequestProducer(request, compressingProducer),
                    SimpleResponseConsumer.create(),
                    new FutureCallback<SimpleHttpResponse>() {

                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(request.getRequestUri() + " -> " + response.getCode());
                            System.out.println(response.getBodyText());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(request.getRequestUri() + " -> " + ex);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(request.getRequestUri() + " cancelled");
                        }

                    });
            future.get();
        }
    }

}