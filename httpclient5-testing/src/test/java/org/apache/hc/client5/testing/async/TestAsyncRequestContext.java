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

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.http2.config.H2Config;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@EnableRuleMigrationSupport
@RunWith(Parameterized.class)
public class TestAsyncRequestContext extends AbstractIntegrationTestBase<CloseableHttpAsyncClient> {

    @Parameterized.Parameters(name = "{0} {1}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                { HttpVersion.HTTP_1_1, URIScheme.HTTP },
                { HttpVersion.HTTP_1_1, URIScheme.HTTPS },
                { HttpVersion.HTTP_2, URIScheme.HTTP },
                { HttpVersion.HTTP_2, URIScheme.HTTPS }
        });
    }

    protected final HttpVersion version;

    public TestAsyncRequestContext(final HttpVersion version, final URIScheme scheme) {
        super(scheme);
        this.version = version;
    }

    HttpAsyncClientBuilder clientBuilder;
    PoolingAsyncClientConnectionManager connManager;

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
            connManager.setDefaultTlsConfig(TlsConfig.custom()
                            .setVersionPolicy(version.greaterEquals(HttpVersion.HTTP_2) ? HttpVersionPolicy.FORCE_HTTP_2 : HttpVersionPolicy.FORCE_HTTP_1)
                    .build());
            clientBuilder = HttpAsyncClientBuilder.create()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionRequestTimeout(TIMEOUT)
                            .build())
                    .setConnectionManager(connManager);
        }

    };

    @Override
    public final HttpHost start() throws Exception {
        if (version.greaterEquals(HttpVersion.HTTP_2)) {
            return super.start(null, H2Config.DEFAULT);
        } else {
            return super.start(null, Http1Config.DEFAULT);
        }
    }

    @Override
    protected CloseableHttpAsyncClient createClient() throws Exception {
        return clientBuilder.build();
    }

    @Test
    public void testRequestContext() throws Exception {
        final AtomicReference<ProtocolVersion> versionRef = new AtomicReference<>();
        clientBuilder.addRequestInterceptorFirst((request, entity, context) ->
                versionRef.set(context.getProtocolVersion()));
        final HttpHost target = start();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/random/2048")
                        .build(), null);
        final SimpleHttpResponse response = future.get();
        assertThat(response, CoreMatchers.notNullValue());
        assertThat(response.getCode(), CoreMatchers.equalTo(200));
        final String body = response.getBodyText();
        assertThat(body, CoreMatchers.notNullValue());
        assertThat(body.length(), CoreMatchers.equalTo(2048));
        assertThat(versionRef.get(), CoreMatchers.equalTo(version));
    }

}
