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

import java.io.InterruptedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/**
 * Demonstrates per-request hard end-to-end timeout (query timeout / request deadline).
 */
public class AsyncClientRequestTimeoutExample {

    public static void main(final String[] args) throws Exception {

        // No default requestTimeout at the client level (leave it opt-in per request).
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.custom().build()) {

            client.start();

            final HttpHost host = new HttpHost("https", "httpbin.org");

            // 1) This one should TIME OUT (server delays ~5s, our requestTimeout is 2s)
            final SimpleHttpRequest willTimeout = SimpleRequestBuilder.get()
                    .setHttpHost(host)
                    .setPath("/delay/5")
                    .build();
            willTimeout.setConfig(RequestConfig.custom()
                    .setRequestTimeout(Timeout.ofSeconds(2))
                    .build());

            System.out.println("Executing (expected timeout): " + willTimeout);

            final Future<SimpleHttpResponse> f1 = client.execute(
                    SimpleRequestProducer.create(willTimeout),
                    SimpleResponseConsumer.create(),
                    new FutureCallback<SimpleHttpResponse>() {
                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(willTimeout + " -> " + new StatusLine(response));
                            System.out.println(response.getBodyText());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(willTimeout + " -> FAILED: " + ex);
                            if (ex instanceof InterruptedIOException) {
                                System.out.println("As expected: hard request timeout triggered.");
                            }
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(willTimeout + " -> CANCELLED");
                        }
                    });

            try {
                f1.get(); // Will throw ExecutionException wrapping InterruptedIOException
            } catch (final ExecutionException ee) {
                final Throwable cause = ee.getCause();
                if (cause instanceof InterruptedIOException) {
                    System.out.println("Future failed with InterruptedIOException (OK): " + cause.getMessage());
                } else {
                    System.out.println("Unexpected failure type: " + cause);
                }
            }

            // 2) This one should SUCCEED (server delays ~1s, our requestTimeout is 3s)
            final SimpleHttpRequest willSucceed = SimpleRequestBuilder.get()
                    .setHttpHost(host)
                    .setPath("/delay/1")
                    .build();
            willSucceed.setConfig(RequestConfig.custom()
                    .setRequestTimeout(Timeout.ofSeconds(3)) // <--- longer budget
                    .build());

            System.out.println("Executing (expected success): " + willSucceed);

            final Future<SimpleHttpResponse> f2 = client.execute(
                    SimpleRequestProducer.create(willSucceed),
                    SimpleResponseConsumer.create(),
                    new FutureCallback<SimpleHttpResponse>() {
                        @Override
                        public void completed(final SimpleHttpResponse response) {
                            System.out.println(willSucceed + " -> " + new StatusLine(response));
                            System.out.println(response.getBodyText());
                        }

                        @Override
                        public void failed(final Exception ex) {
                            System.out.println(willSucceed + " -> FAILED: " + ex);
                        }

                        @Override
                        public void cancelled() {
                            System.out.println(willSucceed + " -> CANCELLED");
                        }
                    });

            f2.get(); // Should complete normally

            System.out.println("Shutting down");
            client.close(CloseMode.GRACEFUL);
        }
    }
}
