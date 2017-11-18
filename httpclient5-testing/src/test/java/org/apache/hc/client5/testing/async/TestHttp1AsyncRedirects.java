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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.H2TlsStrategy;
import org.apache.hc.client5.testing.SSLTestContexts;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.H1Config;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.apache.hc.core5.net.URIBuilder;
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

    @Rule
    public ExternalResource connManagerResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            connManager = PoolingAsyncClientConnectionManagerBuilder.create()
                    .setTlsStrategy(new H2TlsStrategy(SSLTestContexts.createClientSSLContext()))
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
    public ExternalResource clientResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            clientBuilder = HttpAsyncClientBuilder.create()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectionTimeout(TIMEOUT)
                            .setConnectionRequestTimeout(TIMEOUT)
                            .build())
                    .setConnectionManager(connManager);
        }

    };

    public TestHttp1AsyncRedirects(final URIScheme scheme) {
        super(scheme);
    }

    @Override
    protected CloseableHttpAsyncClient createClient() throws Exception {
        return clientBuilder.build();
    }

    @Override
    public final HttpHost start() throws Exception {
        return super.start(null, H1Config.DEFAULT);
    }

    static class NoKeepAliveRedirectService extends AbstractSimpleServerExchangeHandler {

        private final int statuscode;

        public NoKeepAliveRedirectService(final int statuscode) {
            super();
            this.statuscode = statuscode;
        }

        @Override
        protected SimpleHttpResponse handle(
                final SimpleHttpRequest request, final HttpCoreContext context) throws HttpException {
            try {
                final URI requestURI = request.getUri();
                final String path = requestURI.getPath();
                if (path.equals("/oldlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(statuscode);
                    response.addHeader(new BasicHeader("Location",
                            new URIBuilder(requestURI).setPath("/newlocation/").build()));
                    response.addHeader(new BasicHeader("Connection", "close"));
                    return response;
                } else if (path.equals("/newlocation/")) {
                    final SimpleHttpResponse response = new SimpleHttpResponse(HttpStatus.SC_OK);
                    response.setBodyText("Successful redirect", ContentType.TEXT_PLAIN);
                    return response;
                } else {
                    return new SimpleHttpResponse(HttpStatus.SC_NOT_FOUND);
                }
            } catch (final URISyntaxException ex) {
                throw new ProtocolException(ex.getMessage(), ex);
            }
        }

    }

    @Test
    public void testBasicRedirect300() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new NoKeepAliveRedirectService(HttpStatus.SC_MULTIPLE_CHOICES);
            }

        });
        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
        Assert.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect301NoKeepAlive() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new NoKeepAliveRedirectService(HttpStatus.SC_MOVED_PERMANENTLY);
            }

        });

        final HttpHost target = start();
        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());
        Assert.assertEquals(target, new HttpHost(request.getAuthority(), request.getScheme()));
    }

    @Test
    public void testDefaultHeadersRedirect() throws Exception {
        server.register("*", new Supplier<AsyncServerExchangeHandler>() {

            @Override
            public AsyncServerExchangeHandler get() {
                return new NoKeepAliveRedirectService(HttpStatus.SC_MOVED_TEMPORARILY);
            }

        });

        final List<Header> defaultHeaders = new ArrayList<>(1);
        defaultHeaders.add(new BasicHeader(HttpHeaders.USER_AGENT, "my-test-client"));
        clientBuilder.setDefaultHeaders(defaultHeaders);

        final HttpHost target = start();

        final HttpClientContext context = HttpClientContext.create();

        final Future<SimpleHttpResponse> future = httpclient.execute(
                SimpleHttpRequest.get(target, "/oldlocation/"), context, null);
        final HttpResponse response = future.get();
        Assert.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assert.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assert.assertEquals("/newlocation/", request.getRequestUri());

        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assert.assertEquals("my-test-client", header.getValue());
    }

}