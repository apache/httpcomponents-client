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
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
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
import org.junit.Assert;
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
                            .setConnectTimeout(TIMEOUT)
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
            final SimpleHttpRequest get = SimpleHttpRequests.get(target, "/random/2048");
            get.setHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
            final Future<SimpleHttpResponse> future = httpclient.execute(get, null);
            final SimpleHttpResponse response = future.get();
            Assert.assertThat(response, CoreMatchers.notNullValue());
            Assert.assertThat(response.getCode(), CoreMatchers.equalTo(200));
            final String body = response.getBodyText();
            Assert.assertThat(body, CoreMatchers.notNullValue());
            Assert.assertThat(body.length(), CoreMatchers.equalTo(2048));
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
                SimpleHttpRequests.get(target, "/random/2048"), null);
        final SimpleHttpResponse response1 = future1.get();
        Assert.assertThat(response1, CoreMatchers.notNullValue());
        Assert.assertThat(response1.getCode(), CoreMatchers.equalTo(200));
        final String body1 = response1.getBodyText();
        Assert.assertThat(body1, CoreMatchers.notNullValue());
        Assert.assertThat(body1.length(), CoreMatchers.equalTo(2048));


        try (final CloseableHttpAsyncClient httpclient2 = HttpAsyncClients.custom()
                .setConnectionManager(connManager)
                .setConnectionManagerShared(true)
                .build()) {
            httpclient2.start();
            final Future<SimpleHttpResponse> future2 = httpclient2.execute(
                    SimpleHttpRequests.get(target, "/random/2048"), null);
            final SimpleHttpResponse response2 = future2.get();
            Assert.assertThat(response2, CoreMatchers.notNullValue());
            Assert.assertThat(response2.getCode(), CoreMatchers.equalTo(200));
            final String body2 = response2.getBodyText();
            Assert.assertThat(body2, CoreMatchers.notNullValue());
            Assert.assertThat(body2.length(), CoreMatchers.equalTo(2048));
        }

        final Future<SimpleHttpResponse> future3 = httpclient.execute(
                SimpleHttpRequests.get(target, "/random/2048"), null);
        final SimpleHttpResponse response3 = future3.get();
        Assert.assertThat(response3, CoreMatchers.notNullValue());
        Assert.assertThat(response3.getCode(), CoreMatchers.equalTo(200));
        final String body3 = response3.getBodyText();
        Assert.assertThat(body3, CoreMatchers.notNullValue());
        Assert.assertThat(body3.length(), CoreMatchers.equalTo(2048));
    }

}