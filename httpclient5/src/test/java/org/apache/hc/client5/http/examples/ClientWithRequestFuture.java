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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.FutureRequestExecutionService;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

public class ClientWithRequestFuture {

    public static void main(final String[] args) throws Exception {
        // the simplest way to create a HttpAsyncClientWithFuture
        final HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(5)
                .setMaxConnTotal(5)
                .build();
        final CloseableHttpClient httpclient = HttpClientBuilder.create()
                .setConnectionManager(cm)
                .build();
        final ExecutorService execService = Executors.newFixedThreadPool(5);
        try (final FutureRequestExecutionService requestExecService = new FutureRequestExecutionService(
                httpclient, execService)) {
            // Because things are asynchronous, you must provide a HttpClientResponseHandler
            final HttpClientResponseHandler<Boolean> handler = new HttpClientResponseHandler<Boolean>() {
                @Override
                public Boolean handleResponse(final ClassicHttpResponse response) throws IOException {
                    // simply return true if the status was OK
                    return response.getCode() == HttpStatus.SC_OK;
                }
            };

            // Simple request ...
            final HttpGet request1 = new HttpGet("http://httpbin.org/get");
            final FutureTask<Boolean> futureTask1 = requestExecService.execute(request1,
                    HttpClientContext.create(), handler);
            final Boolean wasItOk1 = futureTask1.get();
            System.out.println("It was ok? " + wasItOk1);

            // Cancel a request
            try {
                final HttpGet request2 = new HttpGet("http://httpbin.org/get");
                final FutureTask<Boolean> futureTask2 = requestExecService.execute(request2,
                        HttpClientContext.create(), handler);
                futureTask2.cancel(true);
                final Boolean wasItOk2 = futureTask2.get();
                System.out.println("It was cancelled so it should never print this: " + wasItOk2);
            } catch (final CancellationException e) {
                System.out.println("We cancelled it, so this is expected");
            }

            // Request with a timeout
            final HttpGet request3 = new HttpGet("http://httpbin.org/get");
            final FutureTask<Boolean> futureTask3 = requestExecService.execute(request3,
                    HttpClientContext.create(), handler);
            final Boolean wasItOk3 = futureTask3.get(10, TimeUnit.SECONDS);
            System.out.println("It was ok? " + wasItOk3);

            final FutureCallback<Boolean> callback = new FutureCallback<Boolean>() {
                @Override
                public void completed(final Boolean result) {
                    System.out.println("completed with " + result);
                }

                @Override
                public void failed(final Exception ex) {
                    System.out.println("failed with " + ex.getMessage());
                }

                @Override
                public void cancelled() {
                    System.out.println("cancelled");
                }
            };

            // Simple request with a callback
            final HttpGet request4 = new HttpGet("http://httpbin.org/get");
            // using a null HttpContext here since it is optional
            // the callback will be called when the task completes, fails, or is cancelled
            final FutureTask<Boolean> futureTask4 = requestExecService.execute(request4,
                    HttpClientContext.create(), handler, callback);
            final Boolean wasItOk4 = futureTask4.get(10, TimeUnit.SECONDS);
            System.out.println("It was ok? " + wasItOk4);
        }
    }
}
