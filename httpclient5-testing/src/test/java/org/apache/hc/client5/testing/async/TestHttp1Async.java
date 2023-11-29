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
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class TestHttp1Async extends AbstractHttpAsyncFundamentalsTest<CloseableHttpAsyncClient> {

    public TestHttp1Async(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected H2TestServer startServer() throws Exception {
        return startServer(Http1Config.DEFAULT, null, null);
    }

    @Override
    protected CloseableHttpAsyncClient startClient() throws Exception {
        return startClient(b -> {});
    }

    @ParameterizedTest(name = "{displayName}; concurrent connections: {0}")
    @ValueSource(ints = {5, 1, 20})
    public void testSequentialGetRequestsCloseConnection(final int concurrentConns) throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final CloseableHttpAsyncClient client = startClient();
        final PoolingAsyncClientConnectionManager connManager = connManager();
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
            assertThat(response, CoreMatchers.notNullValue());
            assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final String body = response.getBodyText();
            assertThat(body, CoreMatchers.notNullValue());
            assertThat(body.length(), CoreMatchers.equalTo(2048));
        }
    }

    @Test
    public void testSharedPool() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final CloseableHttpAsyncClient client = startClient();
        final PoolingAsyncClientConnectionManager connManager = connManager();
        final Future<SimpleHttpResponse> future1 = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response1 = future1.get();
        assertThat(response1, CoreMatchers.notNullValue());
        assertThat(response1.getCode(), CoreMatchers.equalTo(200));
        final String body1 = response1.getBodyText();
        assertThat(body1, CoreMatchers.notNullValue());
        assertThat(body1.length(), CoreMatchers.equalTo(2048));


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
            assertThat(response2, CoreMatchers.notNullValue());
            assertThat(response2.getCode(), CoreMatchers.equalTo(200));
            final String body2 = response2.getBodyText();
            assertThat(body2, CoreMatchers.notNullValue());
            assertThat(body2.length(), CoreMatchers.equalTo(2048));
        }

        final Future<SimpleHttpResponse> future3 = client.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response3 = future3.get();
        assertThat(response3, CoreMatchers.notNullValue());
        assertThat(response3.getCode(), CoreMatchers.equalTo(200));
        final String body3 = response3.getBodyText();
        assertThat(body3, CoreMatchers.notNullValue());
        assertThat(body3.length(), CoreMatchers.equalTo(2048));
    }

    @Test
    public void testRequestCancellation() throws Exception {
        final H2TestServer server = startServer();
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final CloseableHttpAsyncClient client = startClient();
        final PoolingAsyncClientConnectionManager connManager = connManager();
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

                executorService.schedule(new Runnable() {

                    @Override
                    public void run() {
                        future.cancel(true);
                    }
                }, i % 5, TimeUnit.MILLISECONDS);

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

                executorService.schedule(new Runnable() {

                    @Override
                    public void run() {
                        future.cancel(true);
                    }
                }, rnd.nextInt(200), TimeUnit.MILLISECONDS);

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
                assertThat(response, CoreMatchers.notNullValue());
                assertThat(response.getCode(), CoreMatchers.equalTo(200));
            }

        } finally {
            executorService.shutdownNow();
        }
    }

}