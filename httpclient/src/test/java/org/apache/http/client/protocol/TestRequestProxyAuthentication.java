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
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.HttpRoutedConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.RouteInfo.LayerType;
import org.apache.http.conn.routing.RouteInfo.TunnelType;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRequestProxyAuthentication {

    @Test(expected=IllegalArgumentException.class)
    public void testRequestParameterCheck() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(null, context);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testContextParameterCheck() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(request, null);
    }

    @Test
    public void testProxyAuthOverPlainConnection() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 443, "https");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        BasicScheme authscheme = new BasicScheme();
        Credentials creds = new UsernamePasswordCredentials("user", "secret");
        AuthScope authscope = new AuthScope("localhost", 8080, "auth-realm", "http");
        BasicHeader challenge = new BasicHeader(AUTH.PROXY_AUTH, "BASIC realm=auth-realm");
        authscheme.processChallenge(challenge);

        AuthState authstate = new AuthState();
        authstate.setAuthScheme(authscheme);
        authstate.setAuthScope(authscope);
        authstate.setCredentials(creds);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, authstate);

        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(AUTH.PROXY_AUTH_RESP);
        Assert.assertNotNull(header);
        Assert.assertEquals("Basic dXNlcjpzZWNyZXQ=", header.getValue());
    }

    @Test
    public void testProxyAuthOverTunneledConnection() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 443, "https");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, true,
                TunnelType.TUNNELLED, LayerType.LAYERED);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        BasicScheme authscheme = new BasicScheme();
        Credentials creds = new UsernamePasswordCredentials("user", "secret");
        AuthScope authscope = new AuthScope("localhost", 8080, "auth-realm", "http");

        BasicHeader challenge = new BasicHeader(AUTH.PROXY_AUTH, "BASIC realm=auth-realm");
        authscheme.processChallenge(challenge);

        AuthState authstate = new AuthState();
        authstate.setAuthScheme(authscheme);
        authstate.setAuthScope(authscope);
        authstate.setCredentials(creds);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, authstate);

        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(AUTH.PROXY_AUTH_RESP);
        Assert.assertNull(header);
    }

    @Test
    public void testPreserveAuthHeader() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        request.addHeader(AUTH.PROXY_AUTH_RESP, "Basic c3R1ZmY6c3R1ZmY=");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 443, "https");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, true,
                TunnelType.TUNNELLED, LayerType.LAYERED);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        BasicScheme authscheme = new BasicScheme();
        Credentials creds = new UsernamePasswordCredentials("user", "secret");
        AuthScope authscope = new AuthScope("localhost", 8080, "auth-realm", "http");

        BasicHeader challenge = new BasicHeader(AUTH.PROXY_AUTH, "BASIC realm=auth-realm");
        authscheme.processChallenge(challenge);

        AuthState authstate = new AuthState();
        authstate.setAuthScheme(authscheme);
        authstate.setAuthScope(authscope);
        authstate.setCredentials(creds);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, authstate);

        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(AUTH.PROXY_AUTH_RESP);
        Assert.assertNotNull(header);
        Assert.assertEquals("Basic c3R1ZmY6c3R1ZmY=", header.getValue());
    }

    @Test
    public void testAuthStateNotSet() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 80, "http");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, null);

        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(AUTH.PROXY_AUTH_RESP);
        Assert.assertNull(header);
    }

    @Test
    public void testAuthSchemeNotSet() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 80, "http");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        AuthState authstate = new AuthState();

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, authstate);

        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(AUTH.PROXY_AUTH_RESP);
        Assert.assertNull(header);
    }

    @Test
    public void testAuthCredentialsNotSet() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 80, "http");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        AuthState authstate = new AuthState();

        BasicScheme authscheme = new BasicScheme();
        BasicHeader challenge = new BasicHeader(AUTH.PROXY_AUTH, "BASIC realm=auth-realm");
        authscheme.processChallenge(challenge);

        authstate.setAuthScheme(authscheme);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, authstate);

        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(AUTH.PROXY_AUTH_RESP);
        Assert.assertNull(header);
    }

    @Test
    public void testConnectionBasedAuthOnlyIfChallenged() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpContext context = new BasicHttpContext();

        HttpHost target = new HttpHost("localhost", 80, "http");
        HttpHost proxy = new HttpHost("localhost", 8080);
        HttpRoute route = new HttpRoute(target, null, proxy, false,
                TunnelType.PLAIN, LayerType.PLAIN);

        HttpRoutedConnection conn = Mockito.mock(HttpRoutedConnection.class);
        Mockito.when(conn.getRoute()).thenReturn(route);

        AuthState authstate = new AuthState();

        BasicScheme authscheme = new BasicScheme() {

            @Override
            public boolean isConnectionBased() {
                return true;
            }

        };

        BasicHeader challenge = new BasicHeader(AUTH.PROXY_AUTH, "BASIC realm=auth-realm");
        authscheme.processChallenge(challenge);

        Credentials creds = new UsernamePasswordCredentials("user", "secret");

        authstate.setAuthScheme(authscheme);
        authstate.setCredentials(creds);
        // No challenge
        authstate.setAuthScope(null);

        context.setAttribute(ExecutionContext.HTTP_CONNECTION, conn);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, authstate);

        HttpRequestInterceptor interceptor = new RequestProxyAuthentication();
        interceptor.process(request, context);
        Header header = request.getFirstHeader(AUTH.PROXY_AUTH_RESP);
        Assert.assertNull(header);
    }

}
