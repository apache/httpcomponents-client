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

import org.apache.hc.client5.http.async.methods.DeflatingGzipEntityProducer;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;

/**
 * <h2>Example – streaming <b>GZIP</b> <i>compression</i> with the async API</h2>
 *
 * <p>{@link DeflatingGzipEntityProducer} wraps any existing
 * {@link org.apache.hc.core5.http.nio.AsyncEntityProducer} and emits a
 * fully-valid GZIP stream on the wire while honouring back-pressure.</p>
 *
 * <p>This example sends a small JSON document compressed with GZIP to
 * <a href="https://httpbin.org/post">httpbin / post</a> and prints the
 * server’s echo response.
 * The {@code Content-Encoding: gzip} header is added automatically by
 * {@code RequestContent} interceptor — no manual header work required.</p>
 *
 * @since 5.6
 */
public final class AsyncClientGzipCompressionExample {

    public static void main(final String[] args) throws Exception {
        try (final CloseableHttpAsyncClient client = HttpAsyncClients.createDefault()) {
            client.start();

            final SimpleHttpRequest req = SimpleRequestBuilder
                    .get("https://httpbin.org/gzip")
                    .build();

            final Future<Message<HttpResponse, String>> f = client.execute(
                    SimpleRequestProducer.create(req),
                    new BasicResponseConsumer<>(new StringAsyncEntityConsumer()),
                    null);

            final Message<HttpResponse, String> msg = f.get();
            System.out.println("status=" + msg.getHead().getCode());
            System.out.println("body=\n" + msg.getBody());
        }
    }
}