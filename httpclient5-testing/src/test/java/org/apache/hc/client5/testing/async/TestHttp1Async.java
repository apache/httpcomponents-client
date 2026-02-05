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

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.testing.extension.async.ClientProtocolLevel;
import org.apache.hc.client5.testing.extension.async.ServerProtocolLevel;
import org.apache.hc.client5.testing.extension.async.TestAsyncClient;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class TestHttp1Async extends AbstractHttpAsyncFundamentalsTest {

    public TestHttp1Async(final URIScheme scheme) {
        this(scheme, false);
    }

    public TestHttp1Async(final URIScheme scheme, final boolean useUnixDomainSocket) {
        super(scheme, ClientProtocolLevel.STANDARD, ServerProtocolLevel.STANDARD, useUnixDomainSocket);
    }

    @ParameterizedTest(name = "{displayName}; concurrent connections: {0}")
    @ValueSource(ints = {5, 1, 20})
    public void testSequentialGetRequestsCloseConnection(final int concurrentConns) throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        connManager.setDefaultMaxPerRoute(concurrentConns);
        connManager.setMaxTotal(100);
        for (int i = 0; i < 3; i++) {
            final Future<SimpleHttpResponse> future = client.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/random/2048")
                            .addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE)
                            .build(), null);
            final SimpleHttpResponse response = future.get();
            Assertions.assertNotNull(response);
            Assertions.assertEquals(200, response.getCode());
            final String body = response.getBodyText();
            Assertions.assertNotNull(body);
            Assertions.assertEquals(2048, body.length());
        }
    }

    @Test
    void testSharedPool() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();

        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        final Future<SimpleHttpResponse> future1 = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response1 = future1.get();
        Assertions.assertNotNull(response1);
        Assertions.assertEquals(200, response1.getCode());
        final String body1 = response1.getBodyText();
        Assertions.assertNotNull(body1);
        Assertions.assertEquals(2048, body1.length());


        try (final CloseableHttpAsyncClient httpclient2 = HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .setConnectionManagerShared(true)
                .build()) {
            httpclient2.start();
            final Future<SimpleHttpResponse> future2 = httpclient2.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/random/2048")
                            .build(), null);
            final SimpleHttpResponse response2 = future2.get();
            Assertions.assertNotNull(response2);
            Assertions.assertEquals(200, response2.getCode());
            final String body2 = response2.getBodyText();
            Assertions.assertNotNull(body2);
            Assertions.assertEquals(2048, body2.length());
        }

        final Future<SimpleHttpResponse> future3 = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response3 = future3.get();
        Assertions.assertNotNull(response3);
        Assertions.assertEquals(200, response3.getCode());
        final String body3 = response3.getBodyText();
        Assertions.assertNotNull(body3);
        Assertions.assertEquals(2048, body3.length());
    }

    @Test
    void testRequestCancellation() throws Exception {
        configureServer(bootstrap -> bootstrap.register("/random/*", AsyncRandomHandler::new));
        final HttpHost target = startServer();

        final TestAsyncClient client = startClient();
        final PoolingAsyncClientConnectionManager connManager = client.getConnectionManager();
        connManager.setDefaultMaxPerRoute(1);
        connManager.setMaxTotal(1);

        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        try {

            for (int i = 0; i < 20; i++) {
                final Future<SimpleHttpResponse> future = client.execute(
                        SimpleRequestBuilder.get()
                                .setHttpHost(target)
                                .setPath("/random/1000")
                                .build(), null);

                executorService.schedule(() -> future.cancel(true), i % 5, TimeUnit.MILLISECONDS);

                try {
                    future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                } catch (final TimeoutException ex) {
                    throw ex;
                } catch (final Exception ignore) {
                }
            }

            final Random rnd = new Random();
            for (int i = 0; i < 20; i++) {
                final Future<SimpleHttpResponse> future = client.execute(
                        SimpleRequestBuilder.get()
                                .setHttpHost(target)
                                .setPath("/random/1000")
                                .build(), null);

                executorService.schedule(() -> future.cancel(true), rnd.nextInt(200), TimeUnit.MILLISECONDS);

                try {
                    future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                } catch (final TimeoutException ex) {
                    throw ex;
                } catch (final Exception ignore) {
                }
            }

            for (int i = 0; i < 5; i++) {
                final Future<SimpleHttpResponse> future = client.execute(
                        SimpleRequestBuilder.get()
                                .setHttpHost(target)
                                .setPath("/random/1000")
                                .build(), null);
                final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                Assertions.assertNotNull(response);
                Assertions.assertEquals(200, response.getCode());
            }

        } finally {
            executorService.shutdownNow();
        }
    }

}
