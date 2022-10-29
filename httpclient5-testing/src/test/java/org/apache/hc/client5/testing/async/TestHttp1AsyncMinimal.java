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

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.impl.async.MinimalHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.nio.AsyncClientEndpoint;
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers;
import org.apache.hc.core5.http.nio.entity.BasicAsyncEntityConsumer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicResponseConsumer;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

public abstract class TestHttp1AsyncMinimal extends AbstractHttpAsyncFundamentalsTest<MinimalHttpAsyncClient> {

    public TestHttp1AsyncMinimal(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected H2TestServer startServer() throws Exception {
        return startServer(Http1Config.DEFAULT, null, null);
    }

    @Override
    protected MinimalHttpAsyncClient startClient() throws Exception {
        return startMinimalClient(
                Http1Config.DEFAULT,
                H2Config.DEFAULT,
                b -> {});
    }

    @Test
    public void testConcurrentPostRequestsSameEndpoint() throws Exception {
        final H2TestServer server = startServer();
        server.register("/echo/*", AsyncEchoHandler::new);
        final HttpHost target = targetHost();

        final MinimalHttpAsyncClient client = startClient();

        final byte[] b1 = new byte[1024];
        final Random rnd = new Random(System.currentTimeMillis());
        rnd.nextBytes(b1);

        final int reqCount = 20;

        final Future<AsyncClientEndpoint> endpointLease = client.lease(target, null);
        final AsyncClientEndpoint endpoint = endpointLease.get(5, TimeUnit.SECONDS);
        try {
            final Queue<Future<Message<HttpResponse, byte[]>>> queue = new LinkedList<>();
            for (int i = 0; i < reqCount; i++) {
                final Future<Message<HttpResponse, byte[]>> future = endpoint.execute(
                        new BasicRequestProducer(Method.GET, target, "/echo/",
                                AsyncEntityProducers.create(b1, ContentType.APPLICATION_OCTET_STREAM)),
                        new BasicResponseConsumer<>(new BasicAsyncEntityConsumer()), HttpClientContext.create(), null);
                queue.add(future);
            }
            while (!queue.isEmpty()) {
                final Future<Message<HttpResponse, byte[]>> future = queue.remove();
                final Message<HttpResponse, byte[]> responseMessage = future.get();
                assertThat(responseMessage, CoreMatchers.notNullValue());
                final HttpResponse response = responseMessage.getHead();
                assertThat(response.getCode(), CoreMatchers.equalTo(200));
                final byte[] b2 = responseMessage.getBody();
                assertThat(b1, CoreMatchers.equalTo(b2));
                endpoint.releaseAndReuse();
            }
        } finally {
            endpoint.releaseAndDiscard();
        }

    }

}