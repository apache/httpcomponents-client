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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.testing.OldPathRedirectResolver;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.client5.testing.redirect.Redirect;
import org.apache.hc.core5.function.Decorator;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Redirection test cases.
 */
@RunWith(Parameterized.class)
public class TestHttp1AsyncRedirects extends AbstractHttpAsyncRedirectsTest<CloseableHttpAsyncClient> {

    @Parameterized.Parameters(name = "HTTP/1.1 {0}")
    public static Collection<Object[]> protocols() {
        return Arrays.asList(new Object[][]{
                {URIScheme.HTTP},
                {URIScheme.HTTPS},
        });
    }

    protected HttpAsyncClientBuilder clientBuilder;
    protected PoolingAsyncClientConnectionManager connManager;

    public TestHttp1AsyncRedirects(final URIScheme scheme) {
        super(HttpVersion.HTTP_1_1, scheme);
    }

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

    @Override
    protected CloseableHttpAsyncClient createClient() throws Exception {
        return clientBuilder.build();
    }

    @Test
    public void testBasicRedirect300NoKeepAlive() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES,
                                Redirect.ConnControl.CLOSE));
            }

        });
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect301NoKeepAlive() throws Exception {
        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY,
                                Redirect.ConnControl.CLOSE));
            }

        });
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/100"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/100", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testDefaultHeadersRedirect() throws Exception {
        final List<Header> defaultHeaders = new ArrayList<>(1);
        defaultHeaders.add(new BasicHeader(HttpHeaders.USER_AGENT, "my-test-client"));
        clientBuilder.setDefaultHeaders(defaultHeaders);

        final HttpHost target = start(new Decorator<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler decorate(final AsyncServerExchangeHandler exchangeHandler) {
                return new RedirectingAsyncDecorator(
                        exchangeHandler,
                        new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY,
                                Redirect.ConnControl.CLOSE));
            }

        });

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequests.get(target, "/oldlocation/123"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/random/123", request.getRequestUri());

        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assert.assertEquals("my-test-client", header.getValue());
    }

}
