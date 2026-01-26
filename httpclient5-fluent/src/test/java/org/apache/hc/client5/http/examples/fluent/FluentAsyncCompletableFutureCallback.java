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
package org.apache.hc.client5.http.examples.fluent;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.hc.client5.http.fluent.Async;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.concurrent.FutureCallback;

/**
 * This example demonstrates how the HttpClient fluent API can be used to execute multiple
 * requests asynchronously using CompletableFuture while also receiving per-request callbacks.
 */
public class FluentAsyncCompletableFutureCallback {

    public static void main(final String... args) throws Exception {

        final List<Request> requests = Arrays.asList(
                Request.get("http://www.google.com/"),
                Request.get("http://www.yahoo.com/"),
                Request.get("http://www.apache.org/"),
                Request.get("http://www.apple.com/")
        );

        final Async async = Async.newInstance().maxThreads(8).queueCapacity(500).useDefaultExecutor();
        try {

            final CompletableFuture<?>[] futures = requests.stream()
                    .map(request -> async.executeAsync(request, new FutureCallback<Content>() {

                                @Override
                                public void completed(final Content content) {
                                    System.out.println("Callback completed: " + request);
                                }

                                @Override
                                public void failed(final Exception ex) {
                                    System.out.println("Callback failed: " + ex.getMessage() + ": " + request);
                                }

                                @Override
                                public void cancelled() {
                                    System.out.println("Callback cancelled: " + request);
                                }

                            }).thenAccept(content -> System.out.println("Future completed: " + request))
                            .exceptionally(ex -> {
                                System.out.println("Future failed: " + ex.getMessage() + ": " + request);
                                return null;
                            }))
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();
        } finally {
            async.shutdown();
        }

        System.out.println("Done");
    }

}
