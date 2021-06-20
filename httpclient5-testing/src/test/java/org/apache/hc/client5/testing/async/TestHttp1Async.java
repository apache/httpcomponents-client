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

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestHttp1Async extends AbstractHttpAsyncFundamentalsTest<CloseableHttpAsyncClient> {

    @Parameterized.Parameters(name = "HTTP/1.1 {0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { URIScheme.HTTP },
                { URIScheme.HTTPS },
        });
    }

    protected HttpAsyncClientBuilder clientBuilder;
    protected PoolingAsyncClientConnectionManager connManager;

    @Rule
    public ExternalResource connManagerResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                    .setTlsStrategy(new DefaultClientTlsStrategy(SSLTestContexts.createClientSSLContext()))
                    .setDefaultConnectionConfig(ConnectionConfig.custom()
                            .setConnectTimeout(TIMEOUT)
                            .setSocketTimeout(TIMEOUT)
                            .build())
                    .build();
        }

        @Override
        protected void after() {
            if (connManager != null) {
                connManager.close();
                connManager = null;
            }
        }

    };

    @Rule
    public ExternalResource clientBuilderResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            clientBuilder = HttpAsyncClientBuilder.create()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(TIMEOUT)
                            .build())
                    .setConnectionManager(connManager);
        }

    };

    public TestHttp1Async(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected CloseableHttpAsyncClient createClient() {
        return clientBuilder.build();
    }

    @Override
    public HttpHost start() throws Exception {
        return super.start(null, Http1Config.DEFAULT);
    }

    @Test
    public void testSequenctialGetRequestsCloseConnection() throws Exception {
        final HttpHost target = start();
        for (int i = 0; i < 3; i++) {
            final Future<SimpleHttpResponse> future = httpclient.execute(
                    SimpleRequestBuilder.get()
                            .setHttpHost(target)
                            .setPath("/random/2048")
                            .addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE)
                            .build(), null);
            final SimpleHttpResponse response = future.get();
            MatcherAssert.assertThat(response, CoreMatchers.notNullValue());
            MatcherAssert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final String body = response.getBodyText();
            MatcherAssert.assertThat(body, CoreMatchers.notNullValue());
            MatcherAssert.assertThat(body.length(), CoreMatchers.equalTo(2048));
        }
    }

    @Test
    public void testConcurrentPostsOverMultipleConnections() throws Exception {
        connManager.setDefaultMaxPerRoute(20);
        connManager.setMaxTotal(100);
        super.testConcurrentPostRequests();
    }

    @Test
    public void testConcurrentPostsOverSingleConnection() throws Exception {
        connManager.setDefaultMaxPerRoute(1);
        connManager.setMaxTotal(100);
        super.testConcurrentPostRequests();
    }

    @Test
    public void testSharedPool() throws Exception {
        final HttpHost target = start();
        final Future<SimpleHttpResponse> future1 = httpclient.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response1 = future1.get();
        MatcherAssert.assertThat(response1, CoreMatchers.notNullValue());
        MatcherAssert.assertThat(response1.getCode(), CoreMatchers.equalTo(200));
        final String body1 = response1.getBodyText();
        MatcherAssert.assertThat(body1, CoreMatchers.notNullValue());
        MatcherAssert.assertThat(body1.length(), CoreMatchers.equalTo(2048));


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
            MatcherAssert.assertThat(response2, CoreMatchers.notNullValue());
            MatcherAssert.assertThat(response2.getCode(), CoreMatchers.equalTo(200));
            final String body2 = response2.getBodyText();
            MatcherAssert.assertThat(body2, CoreMatchers.notNullValue());
            MatcherAssert.assertThat(body2.length(), CoreMatchers.equalTo(2048));
        }

        final Future<SimpleHttpResponse> future3 = httpclient.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response3 = future3.get();
        MatcherAssert.assertThat(response3, CoreMatchers.notNullValue());
        MatcherAssert.assertThat(response3.getCode(), CoreMatchers.equalTo(200));
        final String body3 = response3.getBodyText();
        MatcherAssert.assertThat(body3, CoreMatchers.notNullValue());
        MatcherAssert.assertThat(body3.length(), CoreMatchers.equalTo(2048));
    }

    @Test
    public void testRequestCancellation() throws Exception {
        this.connManager.setDefaultMaxPerRoute(1);
        this.connManager.setMaxTotal(1);

        final HttpHost target = start();

        final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        try {

            for (int i = 0; i < 20; i++) {
                final Future<SimpleHttpResponse> future = httpclient.execute(
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
                final Future<SimpleHttpResponse> future = httpclient.execute(
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
                final Future<SimpleHttpResponse> future = httpclient.execute(
                        SimpleRequestBuilder.get()
                                .setHttpHost(target)
                                .setPath("/random/1000")
                                .build(), null);
                final SimpleHttpResponse response = future.get(TIMEOUT.getDuration(), TIMEOUT.getTimeUnit());
                MatcherAssert.assertThat(response, CoreMatchers.notNullValue());
                MatcherAssert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            }

        } finally {
            executorService.shutdownNow();
        }
    }

}