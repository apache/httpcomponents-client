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
package org.apache.hc.client5.http.async.methods;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.entity.compress.ContentCoding;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.nio.AsyncEntityConsumer;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestDecompressingClient {

    private CloseableHttpAsyncClient client;

    @BeforeEach
    void setUp() {
        client = HttpAsyncClients.createDefault();
        client.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close(CloseMode.GRACEFUL);
        }
    }

    @Test
    void testGzipDecompression() throws Exception {
        final SimpleHttpRequest request = SimpleRequestBuilder.get()
                .setUri(new URI("https://httpbin.org/gzip"))
                .addHeader("Accept-Encoding", ContentCoding.GZIP.name())
                .build();

        final AsyncRequestProducer producer = SimpleRequestProducer.create(request);
        final AsyncEntityConsumer<String> consumer = new DecompressingStringAsyncEntityConsumer();
        final AsyncResponseConsumer<Message<HttpResponse, String>> responseConsumer =
                new BasicResponseConsumer<>(consumer);

        final CountDownLatch latch = new CountDownLatch(1);

        final FutureCallback<Message<HttpResponse, String>> callback = new FutureCallback<Message<HttpResponse, String>>() {
            @Override
            public void completed(final Message<HttpResponse, String> result) {
                try {
                    assertNotNull(result);
                    assertNotNull(result.getBody());
                    assertTrue(result.getBody().contains("gzipped"));
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void failed(final Exception ex) {
                latch.countDown();
                fail("Request failed: " + ex.getMessage());
            }

            @Override
            public void cancelled() {
                latch.countDown();
                fail("Request was cancelled");
            }
        };

        client.execute(producer, responseConsumer, HttpCoreContext.create(), callback);

        if (!latch.await(10, TimeUnit.SECONDS)) {
            fail("Request timed out");
        }
    }
}
