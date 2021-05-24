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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.apache.hc.core5.util.TimeValue;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestHttp1RequestReExecution extends AbstractIntegrationTestBase<CloseableHttpAsyncClient> {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> protocolVersions() {
        return Arrays.asList(new Object[][]{
                { HttpVersion.HTTP_1_1 },
                { HttpVersion.HTTP_2 }
        });
    }

    private final HttpVersion version;

    public TestHttp1RequestReExecution(final HttpVersion version) {
        super(URIScheme.HTTP);
        this.version = version;
    }

    HttpAsyncClientBuilder clientBuilder;
    PoolingAsyncClientConnectionManager connManager;

    @Rule
    public ExternalResource connManagerResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            connManager = PoolingAsyncClientConnectionManagerBuilder.create()
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
                    .setConnectionManager(connManager)
                    .setVersionPolicy(version.greaterEquals(HttpVersion.HTTP_2) ? HttpVersionPolicy.FORCE_HTTP_2 : HttpVersionPolicy.FORCE_HTTP_1);
        }

    };

    @Override
    public final HttpHost start() throws Exception {

        final Resolver<HttpRequest, TimeValue> serviceAvailabilityResolver = new Resolver<HttpRequest, TimeValue>() {

            private final AtomicInteger count = new AtomicInteger(0);

            @Override
            public TimeValue resolve(final HttpRequest request) {
                final int n = count.incrementAndGet();
                return n <= 3 ? TimeValue.ofSeconds(1) : null;
            }

        };

        if (version.greaterEquals(HttpVersion.HTTP_2)) {
            return super.start(null, handler -> new ServiceUnavailableAsyncDecorator(handler, serviceAvailabilityResolver), H2Config.DEFAULT);
        } else {
            return super.start(null, handler -> new ServiceUnavailableAsyncDecorator(handler, serviceAvailabilityResolver), Http1Config.DEFAULT);
        }
    }

    @Override
    protected CloseableHttpAsyncClient createClient() throws Exception {
        return clientBuilder.build();
    }

    @Test
    public void testGiveUpAfterOneRetry() throws Exception {
        clientBuilder.setRetryStrategy(new DefaultHttpRequestRetryStrategy(1, TimeValue.ofSeconds(1)));
        final HttpHost target = start();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response = future.get();
        MatcherAssert.assertThat(response, CoreMatchers.notNullValue());
        MatcherAssert.assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_SERVICE_UNAVAILABLE));
    }

    @Test
    public void testDoNotGiveUpEasily() throws Exception {
        clientBuilder.setRetryStrategy(new DefaultHttpRequestRetryStrategy(5, TimeValue.ofSeconds(1)));
        final HttpHost target = start();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response = future.get();
        MatcherAssert.assertThat(response, CoreMatchers.notNullValue());
        MatcherAssert.assertThat(response.getCode(), CoreMatchers.equalTo(HttpStatus.SC_OK));
    }

}
