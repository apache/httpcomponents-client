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

package org.apache.hc.client5.http.impl.async;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.core5.reactor.SocketChannelFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SocketChannelFactoryBuilderTest {

    @Test
    void testHttpAsyncClientBuilderUsesSocketChannelFactory() throws Exception {
        final AtomicInteger invocationCount = new AtomicInteger();
        final IOException expected = new IOException("custom transport");
        final SocketChannelFactory socketChannelFactory = remoteAddress -> {
            invocationCount.incrementAndGet();
            throw expected;
        };
        try (CloseableHttpAsyncClient client = HttpAsyncClientBuilder.create()
                .setSocketChannelFactory(socketChannelFactory)
                .disableAutomaticRetries()
                .build()) {
            assertFactoryUsed(client, invocationCount);
        }
    }

    @Test
    void testH2AsyncClientBuilderUsesSocketChannelFactory() throws Exception {
        final AtomicInteger invocationCount = new AtomicInteger();
        final IOException expected = new IOException("custom transport");
        final SocketChannelFactory socketChannelFactory = remoteAddress -> {
            invocationCount.incrementAndGet();
            throw expected;
        };
        try (CloseableHttpAsyncClient client = H2AsyncClientBuilder.create()
                .setSocketChannelFactory(socketChannelFactory)
                .disableAutomaticRetries()
                .build()) {
            assertFactoryUsed(client, invocationCount);
        }
    }

    private static void assertFactoryUsed(
            final CloseableHttpAsyncClient client,
            final AtomicInteger invocationCount) throws Exception {
        client.start();
        final Future<SimpleHttpResponse> future = client.execute(
                SimpleRequestBuilder.get("http://localhost:18080/").build(), null);
        Assertions.assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        Assertions.assertEquals(1, invocationCount.get());
    }

}
