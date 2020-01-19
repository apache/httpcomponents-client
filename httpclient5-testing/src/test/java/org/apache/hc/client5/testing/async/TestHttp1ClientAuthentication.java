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

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.BasicTestAuthenticator;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.impl.HttpProcessors;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestHttp1ClientAuthentication extends AbstractHttpAsyncClientAuthentication<CloseableHttpAsyncClient> {

    @Parameterized.Parameters(name = "HTTP/1.1 {0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {URIScheme.HTTP},
                {URIScheme.HTTPS},
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

    public TestHttp1ClientAuthentication(final URIScheme scheme) {
        super(scheme, HttpVersion.HTTP_1_1);
    }

    @Override
    void setDefaultAuthSchemeRegistry(final Lookup<AuthSchemeFactory> authSchemeRegistry) {
        clientBuilder.setDefaultAuthSchemeRegistry(authSchemeRegistry);
    }

    @Override
    void setTargetAuthenticationStrategy(final AuthenticationStrategy targetAuthStrategy) {
        clientBuilder.setTargetAuthenticationStrategy(targetAuthStrategy);
    }

    @Override
    protected CloseableHttpAsyncClient createClient() throws Exception {
        return clientBuilder.build();
    }

    @Test
    public void testBasicAuthenticationSuccessNonPersistentConnection() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new AsyncEchoHandler();
            }

        });
        final HttpHost target = start(
                HttpProcessors.server(),
                new Decorator<AsyncServerExchangeHandler>() {

                    @Override
                    public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                        return new AuthenticatingAsyncDecorator(exchangeHandler, new BasicTestAuthenticator("test:test", "test realm")) {

                            @Override
                            protected void customizeUnauthorizedResponse(final HttpResponse unauthorized) {
                                unauthorized.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
                            }
                        };
                    }

                },
                Http1Config.DEFAULT);

        final TestCredentialsProvider credsProvider = new TestCredentialsProvider(
                new UsernamePasswordCredentials("test", "test".toCharArray()));
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        final Future<SimpleHttpResponse> future = httpclient.execute(SimpleHttpRequests.get(target, "/"), context, null);
        final HttpResponse response = future.get();

        Assert.assertNotNull(response);
        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        final AuthScope authscope = credsProvider.getAuthScope();
        Assert.assertNotNull(authscope);
        Assert.assertEquals("test realm", authscope.getRealm());
    }

}