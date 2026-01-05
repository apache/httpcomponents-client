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
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.H2AsyncClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;

/**
 * Demonstrates client-level request throttling for the HTTP/2 async client using a shared FIFO queue.
 * The {@link H2AsyncClientBuilder#setMaxQueuedRequests(int)} setting caps the number of concurrently
 * executing requests for the client instance; requests submitted beyond that limit are queued in FIFO
 * order and dispatched as in-flight slots become available.
 *
 * @since 5.7
 */
public final class H2SharedClientQueueLocalExample {

    public static void main(final String[] args) throws Exception {

        final CloseableHttpAsyncClient client = H2AsyncClientBuilder.create()
                .setMaxQueuedRequests(2)
                .build();

        client.start();

        final HttpHost target = new HttpHost("https", "nghttp2.org");
        final String[] paths = {"/httpbin", "/httpbin/ip", "/httpbin/user-agent", "/httpbin/headers"};

        for (final String path : paths) {
            final SimpleHttpRequest request = SimpleRequestBuilder.get()
                    .setHttpHost(target)
                    .setPath(path)
                    .build();

            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestProducer.create(request),
                    SimpleResponseConsumer.create(),
                    null);
            final SimpleHttpResponse response = future.get();
            System.out.println(request + " -> " + new StatusLine(response));
        }

        client.close(CloseMode.GRACEFUL);
    }

    private H2SharedClientQueueLocalExample() {
    }

}
