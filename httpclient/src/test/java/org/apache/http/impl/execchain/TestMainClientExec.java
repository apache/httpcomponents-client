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
package org.apache.http.impl.execchain;

import java.util.concurrent.TimeUnit;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.protocol.HttpRequestExecutor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestMainClientExec {

    private MainClientExec mainClientExec;
    private HttpRequestExecutor requestExecutor;
    private HttpClientConnectionManager connManager;
    private ConnectionReuseStrategy reuseStrategy;
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    private AuthenticationStrategy targetAuthStrategy;
    private AuthenticationStrategy proxyAuthStrategy;
    private UserTokenHandler userTokenHandler;
    private HttpClientContext context;
    private HttpGet request;
    private HttpExecutionAware execAware;
    private HttpRoute route;
    private ConnectionRequest connRequest;
    private HttpClientConnection managedConn;
    private HttpResponse response;
    private RequestConfig config;

    @Before
    public void setup() throws Exception {
        requestExecutor = Mockito.mock(HttpRequestExecutor.class);
        connManager = Mockito.mock(HttpClientConnectionManager.class);
        reuseStrategy = Mockito.mock(ConnectionReuseStrategy.class);
        keepAliveStrategy = Mockito.mock(ConnectionKeepAliveStrategy.class);
        targetAuthStrategy = Mockito.mock(AuthenticationStrategy.class);
        proxyAuthStrategy = Mockito.mock(AuthenticationStrategy.class);
        userTokenHandler = Mockito.mock(UserTokenHandler.class);
        mainClientExec = new MainClientExec(requestExecutor, connManager, reuseStrategy,
            keepAliveStrategy, targetAuthStrategy, proxyAuthStrategy, userTokenHandler);
        route = new HttpRoute(new HttpHost("foo", 8080));
        context = new HttpClientContext();
        config = RequestConfig.custom().setSocketTimeout(3000).build();
        context.setRequestConfig(config);
        execAware = Mockito.mock(HttpExecutionAware.class);
        connRequest = Mockito.mock(ConnectionRequest.class);
        managedConn = Mockito.mock(HttpClientConnection.class);
        response = Mockito.mock(HttpResponse.class);
        Mockito.when(
            connManager.requestConnection(Mockito.any(HttpRoute.class), Mockito.any(Object.class)))
            .thenReturn(connRequest);
        Mockito.when(connRequest.get(Mockito.anyLong(), Mockito.any(TimeUnit.class))).thenReturn(
            managedConn);
        managedConn.setSocketTimeout(Mockito.eq(3000));
        Mockito.when(
            requestExecutor.execute(Mockito.any(HttpRequest.class),
                Mockito.any(HttpClientConnection.class), Mockito.any(HttpClientContext.class)))
            .thenReturn(response);
    }

    @Test
    public void testSocketTimeoutNewConnection() throws Exception {
        request = new HttpGet("http://bar/test");
        request.setConfig(config);
        mainClientExec.execute(route, HttpRequestWrapper.wrap(request), context, execAware);
        Mockito.verify(managedConn).setSocketTimeout(3000);
    }

    @Test
    public void testSocketTimeoutExistingConnection() throws Exception {
        request = new HttpGet("http://bar/test");
        request.setConfig(config);
        Mockito.when(managedConn.isOpen()).thenReturn(true);
        mainClientExec.execute(route, HttpRequestWrapper.wrap(request), context, execAware);
        Mockito.verify(managedConn).setSocketTimeout(3000);
    }

    @Test
    public void testSocketTimeoutReset() throws Exception {
        request = new HttpGet("http://bar/test");
        config = RequestConfig.custom().build();
        request.setConfig(config);
        context.setRequestConfig(config);
        Mockito.when(managedConn.isOpen()).thenReturn(true);
        mainClientExec.execute(route, HttpRequestWrapper.wrap(request), context, execAware);
        Mockito.verify(managedConn).setSocketTimeout(0);
    }
}
