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
import java.util.List;
import java.util.concurrent.Future;

import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.testing.OldPathRedirectResolver;
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
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncServerExchangeHandler;
import org.apache.hc.core5.testing.nio.H2TestServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Redirection test cases.
 */
public abstract class TestHttp1AsyncRedirects extends AbstractHttpAsyncRedirectsTest<CloseableHttpAsyncClient> {

    public TestHttp1AsyncRedirects(final URIScheme scheme) {
        super(scheme, HttpVersion.HTTP_1_1);
    }

    @Override
    protected H2TestServer startServer(final Decorator<AsyncServerExchangeHandler> exchangeHandlerDecorator) throws Exception {
        return startServer(Http1Config.DEFAULT, null, exchangeHandlerDecorator);
    }

    @Override
    protected CloseableHttpAsyncClient startClient() throws Exception {
        return startClient(b -> {});
    }

    @Test
    public void testBasicRedirect300NoKeepAlive() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MULTIPLE_CHOICES,
                        Redirect.ConnControl.CLOSE)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final CloseableHttpAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_MULTIPLE_CHOICES, response.getCode());
        Assertions.assertEquals("/oldlocation/", request.getRequestUri());
    }

    @Test
    public void testBasicRedirect301NoKeepAlive() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY,
                        Redirect.ConnControl.CLOSE)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final CloseableHttpAsyncClient client = startClient();

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath("/oldlocation/100")
                        .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/100", request.getRequestUri());
        Assertions.assertEquals(target, new HttpHost(request.getScheme(), request.getAuthority()));
    }

    @Test
    public void testDefaultHeadersRedirect() throws Exception {
        final H2TestServer server = startServer(exchangeHandler -> new RedirectingAsyncDecorator(
                exchangeHandler,
                new OldPathRedirectResolver("/oldlocation", "/random", HttpStatus.SC_MOVED_PERMANENTLY,
                        Redirect.ConnControl.CLOSE)));
        server.register("/random/*", AsyncRandomHandler::new);
        final HttpHost target = targetHost();

        final List<Header> defaultHeaders = new ArrayList<>(1);
        defaultHeaders.add(new BasicHeader(HttpHeaders.USER_AGENT, "my-test-client"));
        final CloseableHttpAsyncClient client = startClient(builder -> builder
                .setDefaultHeaders(defaultHeaders)
        );

        final HttpClientContext context = HttpClientContext.create();
        final Future<SimpleHttpResponse> future = client.execute(SimpleRequestBuilder.get()
                .setHttpHost(target)
                .setPath("/oldlocation/123")
                .build(), context, null);
        final HttpResponse response = future.get();
        Assertions.assertNotNull(response);

        final HttpRequest request = context.getRequest();

        Assertions.assertEquals(HttpStatus.SC_OK, response.getCode());
        Assertions.assertEquals("/random/123", request.getRequestUri());

        final Header header = request.getFirstHeader(HttpHeaders.USER_AGENT);
        Assertions.assertEquals("my-test-client", header.getValue());
    }

}
