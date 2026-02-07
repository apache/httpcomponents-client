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
package org.apache.hc.client5.testing.async;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

abstract class TestHttpAsyncRequestMultiplexing extends AbstractIntegrationTestBase {

    public TestHttpAsyncRequestMultiplexing(final URIScheme uriScheme) {
        super(uriScheme, ClientProtocolLevel.MINIMAL, ServerProtocolLevel.H2_ONLY);
    }

    @Test
    void testConcurrentPostRequests() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/echo/*", AsyncEchoHandler::new));
        configureClient(custimizer -> custimizer
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_2)
                        .build())
                .useMessageMultiplexing()
        );
        final HttpHost target = startServer();
        final TestAsyncClient client = startClient();
        final byte[] b1 = new byte[1024];
        final Random rnd = new Random(System.currentTimeMillis());
        rnd.nextBytes(b1);

        final int reqCount = 200;

        final Queue<Future<Message<HttpResponse, byte[]>>> queue = new LinkedList<>();
        for (int i = 0; i < reqCount; i++) {
            final Future<Message<HttpResponse, byte[]>> future = client.execute(
                    new BasicRequestProducer(Method.POST, target, "/echo/",
                            AsyncEntityProducers.create(b1, ContentType.APPLICATION_OCTET_STREAM)),
                    new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()), HttpClientContext.create(), null);
            queue.add(future);
        }

        while (!queue.isEmpty()) {
            final Future<Message<HttpResponse, byte[]>> future = queue.remove();
            final Message<HttpResponse, byte[]> responseMessage = future.get();
            Assertions.assertNotNull(responseMessage);
            final HttpResponse response = responseMessage.getHead();
            Assertions.assertEquals(200, response.getCode());
            final byte[] b2 = responseMessage.getBody();
            Assertions.assertArrayEquals(b1, b2);
        }
    }

}