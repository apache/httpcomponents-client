/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.protocol;

import junit.framework.Assert;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.conn.HttpRoutedConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.RouteInfo.LayerType;
import org.apache.http.conn.routing.RouteInfo.TunnelType;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRequestClientConnControl {

    @Test(expected=IllegalArgumentException.class)
    public void testRequestParameterCheck() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(null, context);
    }

    @Test
    public void testConnectionKeepAliveForConnectRequest() throws Exception {
        HttpRequest request = new BasicHttpRequest("CONNECT", "www.somedomain.com");
        HttpContext context = new BasicHttpContext();

        HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, context);
        Header header1 = request.getFirstHeader("Proxy-Connection");
        Assert.assertNotNull(header1);
        Assert.assertEquals(HTTP.CONN_KEEP_ALIVE, header1.getValue());
        Header header2 = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNull(header2);
    }

    @Test
    public void testConnectionKeepAliveForDirectRequests() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 80, "http");
        HttpRoute route = new HttpRoute(target, null, false);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, context);

        Header header1 = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header1);
        Assert.assertEquals(HTTP.CONN_KEEP_ALIVE, header1.getValue());
        Header header2 = request.getFirstHeader("Proxy-Connection");
        Assert.assertNull(header2);
    }

    @Test
    public void testConnectionKeepAliveForTunneledRequests() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 443, "https");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, true,
                TunnelType.TUNNELLED, LayerType.LAYERED);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, context);

        Header header1 = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header1);
        Assert.assertEquals(HTTP.CONN_KEEP_ALIVE, header1.getValue());
        Header header2 = request.getFirstHeader("Proxy-Connection");
        Assert.assertNull(header2);
    }

    @Test
    public void testProxyConnectionKeepAliveForRequestsOverProxy() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 80, "http");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, context);

        Header header1 = request.getFirstHeader("Proxy-Connection");
        Assert.assertNotNull(header1);
        Assert.assertEquals(HTTP.CONN_KEEP_ALIVE, header1.getValue());
        Header header2 = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNull(header2);
    }

    @Test
    public void testPreserveCustomConnectionHeader() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 443, "https");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, true,
                TunnelType.TUNNELLED, LayerType.LAYERED);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, context);

        Header header1 = request.getFirstHeader(HTTP.CONN_DIRECTIVE);
        Assert.assertNotNull(header1);
        Assert.assertEquals(HTTP.CONN_CLOSE, header1.getValue());
        Header header2 = request.getFirstHeader("Proxy-Connection");
        Assert.assertNull(header2);
    }

    @Test
    public void testPreserveCustomProxyConnectionHeader() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader("Proxy-Connection", HTTP.CONN_CLOSE);
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 80, "http");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);

        HttpRequestInterceptor interceptor = new RequestClientConnControl();
        interceptor.process(request, context);

        Header header1 = request.getFirstHeader("Proxy-Connection");
        Assert.assertNotNull(header1);
        Assert.assertEquals(HTTP.CONN_CLOSE, header1.getValue());
    }

}
