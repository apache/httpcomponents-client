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

package org.apache.hc.client5.http.protocol;

import javax.net.ssl.SSLSession;

import org.apache.hc.client5.http.HeadersMatcher;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestRequestUpgrade {

    private RequestUpgrade interceptor;
    private HttpClientContext context;

    @BeforeEach
    void setUp() {
        interceptor = new RequestUpgrade();
        context = HttpClientContext.create();
    }

    @Test
    void testUpgrade() throws Exception {
        final HttpRequest get = new BasicHttpRequest("GET", "/");
        interceptor.process(get, null, context);
        MatcherAssert.assertThat(get.getHeaders(),
                HeadersMatcher.same(
                        new BasicHeader(HttpHeaders.UPGRADE, "TLS/1.2"),
                        new BasicHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)));
        final HttpRequest options = new BasicHttpRequest("OPTIONS", "/");
        interceptor.process(options, null, context);
        MatcherAssert.assertThat(options.getHeaders(),
                HeadersMatcher.same(
                        new BasicHeader(HttpHeaders.UPGRADE, "TLS/1.2"),
                        new BasicHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)));
        final HttpRequest head = new BasicHttpRequest("HEAD", "/");
        interceptor.process(head, null, context);
        MatcherAssert.assertThat(head.getHeaders(),
                HeadersMatcher.same(
                        new BasicHeader(HttpHeaders.UPGRADE, "TLS/1.2"),
                        new BasicHeader(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE)));
    }

    @Test
    void testUpgradeDisabled() throws Exception {
        context.setRequestConfig(RequestConfig.custom()
                .setProtocolUpgradeEnabled(false)
                .build());
        final HttpRequest get = new BasicHttpRequest("GET", "/");
        interceptor.process(get, null, context);
        Assertions.assertFalse(get.containsHeader(HttpHeaders.UPGRADE));
    }

    @Test
    void testDoNotUpgradeHTTP2() throws Exception {
        context.setProtocolVersion(HttpVersion.HTTP_2);
        final HttpRequest get = new BasicHttpRequest("GET", "/");
        interceptor.process(get, null, context);
        Assertions.assertFalse(get.containsHeader(HttpHeaders.UPGRADE));
    }

    @Test
    void testDoNotUpgradeHTTP10() throws Exception {
        context.setProtocolVersion(HttpVersion.HTTP_1_0);
        final HttpRequest get = new BasicHttpRequest("GET", "/");
        interceptor.process(get, null, context);
        Assertions.assertFalse(get.containsHeader(HttpHeaders.UPGRADE));
    }

    @Test
    void testDoUpgradeIfAlreadyTLS() throws Exception {
        context.setSSLSession(Mockito.mock(SSLSession.class));
        final HttpRequest get = new BasicHttpRequest("GET", "/");
        interceptor.process(get, null, context);
        Assertions.assertFalse(get.containsHeader(HttpHeaders.UPGRADE));
    }

    @Test
    void testDoUpgradeIfConnectionHeaderPresent() throws Exception {
        final HttpRequest get = new BasicHttpRequest("GET", "/");
        get.addHeader(HttpHeaders.CONNECTION, "keep-alive");
        interceptor.process(get, null, context);
        Assertions.assertFalse(get.containsHeader(HttpHeaders.UPGRADE));
    }

    @Test
    void testDoUpgradeNonSafeMethodsOrTrace() throws Exception {
        final HttpRequest post = new BasicHttpRequest("POST", "/");
        interceptor.process(post, null, context);
        Assertions.assertFalse(post.containsHeader(HttpHeaders.UPGRADE));

        final HttpRequest put = new BasicHttpRequest("PUT", "/");
        interceptor.process(put, null, context);
        Assertions.assertFalse(put.containsHeader(HttpHeaders.UPGRADE));

        final HttpRequest patch = new BasicHttpRequest("PATCH", "/");
        interceptor.process(patch, null, context);
        Assertions.assertFalse(patch.containsHeader(HttpHeaders.UPGRADE));

        final HttpRequest trace = new BasicHttpRequest("TRACE", "/");
        interceptor.process(trace, null, context);
        Assertions.assertFalse(trace.containsHeader(HttpHeaders.UPGRADE));
    }

    @Test
    void doesNotAddUpgradeHeadersWhenProxyPresent() throws Exception {
        final RequestUpgrade interceptor = new RequestUpgrade();

        final HttpHost target = new HttpHost("http", "example.com", 80);
        final HttpHost proxy = new HttpHost("http", "proxy.local", 8080);

        final HttpClientContext ctx = HttpClientContext.create();
        final HttpRoute route = new HttpRoute(target, null, proxy, false);
        ctx.setRoute(route);
        ctx.setRequestConfig(RequestConfig.custom()
                .setProtocolUpgradeEnabled(true)
                .build());

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setScheme("http");

        interceptor.process(request, null, ctx);

        Assertions.assertNull(request.getFirstHeader(HttpHeaders.UPGRADE), "Upgrade header must be absent over proxy");
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.CONNECTION), "Connection: upgrade must be absent over proxy");
    }

    @Test
    void doesNotAddUpgradeHeadersWhenSchemeIsHttps() throws Exception {
        final RequestUpgrade interceptor = new RequestUpgrade();

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom()
                .setProtocolUpgradeEnabled(true)
                .build());

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.setScheme("https");

        interceptor.process(request, null, ctx);

        Assertions.assertNull(request.getFirstHeader(HttpHeaders.UPGRADE));
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.CONNECTION));
    }

    @Test
    void addsUpgradeHeadersWhenSchemeIsHttp() throws Exception {
        final RequestUpgrade interceptor = new RequestUpgrade();

        final HttpClientContext ctx = HttpClientContext.create();
        ctx.setRequestConfig(RequestConfig.custom()
                .setProtocolUpgradeEnabled(true)
                .build());

        final BasicClassicHttpRequest request = new BasicClassicHttpRequest("GET", "/");
        request.setVersion(HttpVersion.HTTP_1_1);
        request.setScheme("http");

        interceptor.process(request, null, ctx);

        Assertions.assertNotNull(request.getFirstHeader(HttpHeaders.UPGRADE));
        Assertions.assertNotNull(request.getFirstHeader(HttpHeaders.CONNECTION));
    }
}
