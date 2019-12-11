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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo.LayerType;
import org.apache.hc.client5.http.RouteInfo.TunnelType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.Assert;
import org.junit.Test;

public class TestRequestClientConnControl {

    @Test(expected=NullPointerException.class)
    public void testRequestParameterCheck() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(null, null, context);
    }

    @Test
    public void testConnectionKeepAliveForConnectRequest() throws Exception {
        final HttpRequest request = new BasicHttpRequest("CONNECT", "www.somedomain.com");
        final HttpClientContext context = HttpClientContext.create();

        final HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, null, context);
        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNull(header);
    }

    @Test
    public void testConnectionKeepAliveForDirectRequests() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpClientContext context = HttpClientContext.create();

        final HttpHost target = new HttpHost("http", "localhost", 80);
        final HttpRoute route = new HttpRoute(target, null, false);

        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);

        final HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, null, context);

        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals(HeaderElements.KEEP_ALIVE, header.getValue());
    }

    @Test
    public void testConnectionKeepAliveForTunneledRequests() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpClientContext context = HttpClientContext.create();

        final HttpHost target = new HttpHost("https", "localhost", 443);
        final HttpHost proxy = new HttpHost("localhost", 8080);
        final HttpRoute route = new HttpRoute(target, null, proxy, true,
                TunnelType.TUNNELLED, LayerType.LAYERED);

        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);

        final HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, null, context);

        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals(HeaderElements.KEEP_ALIVE, header.getValue());
    }

    @Test
    public void testProxyConnectionKeepAliveForRequestsOverProxy() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpClientContext context = HttpClientContext.create();

        final HttpHost target = new HttpHost("http", "localhost", 80);
        final HttpHost proxy = new HttpHost("localhost", 8080);
        final HttpRoute route = new HttpRoute(target, null, proxy, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);

        final HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, null, context);

        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNull(header);
    }

    @Test
    public void testPreserveCustomConnectionHeader() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HttpHeaders.CONNECTION, HeaderElements.CLOSE);
        final HttpClientContext context = HttpClientContext.create();

        final HttpHost target = new HttpHost("https", "localhost", 443);
        final HttpHost proxy = new HttpHost("localhost", 8080);
        final HttpRoute route = new HttpRoute(target, null, proxy, true,
                TunnelType.TUNNELLED, LayerType.LAYERED);

        context.setAttribute(HttpClientContext.HTTP_ROUTE, route);

        final HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, null, context);

        final Header header = request.getFirstHeader(HttpHeaders.CONNECTION);
        Assert.assertNotNull(header);
        Assert.assertEquals(HeaderElements.CLOSE, header.getValue());
    }

}
