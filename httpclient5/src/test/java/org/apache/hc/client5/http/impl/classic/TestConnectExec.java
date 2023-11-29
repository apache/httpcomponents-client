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
package org.apache.hc.client5.http.impl.classic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.TunnelRefusedException;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestConnectExec {

    @Mock
    private ConnectionReuseStrategy reuseStrategy;
    @Mock
    private HttpProcessor proxyHttpProcessor;
    @Mock
    private AuthenticationStrategy proxyAuthStrategy;
    @Mock
    private ExecRuntime execRuntime;
    @Mock
    private ExecChain execChain;

    private ConnectExec exec;
    private HttpHost target;
    private HttpHost proxy;

    @BeforeEach
    public void setup() throws Exception {
        MockitoAnnotations.openMocks(this);
        exec = new ConnectExec(reuseStrategy, proxyHttpProcessor, proxyAuthStrategy, null, true);
        target = new HttpHost("foo", 80);
        proxy = new HttpHost("bar", 8888);
    }

    @Test
    public void testExecAcquireConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        try (final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK")) {
            response.setEntity(EntityBuilder.create()
                    .setStream(new ByteArrayInputStream(new byte[]{}))
                    .build());
        }
        context.setUserToken("Blah");

        Mockito.when(execRuntime.isEndpointAcquired()).thenReturn(false);
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        exec.execute(request, scope, execChain);
        Mockito.verify(execRuntime).acquireEndpoint("test", route, "Blah", context);
        Mockito.verify(execRuntime).connectEndpoint(context);
    }

    @Test
    public void testEstablishDirectRoute() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(execRuntime).connectEndpoint(Mockito.any());
        Mockito.when(execRuntime.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        exec.execute(request, scope, execChain);

        Mockito.verify(execRuntime).connectEndpoint(context);
        Mockito.verify(execRuntime, Mockito.never()).execute(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any());
    }

    @Test
    public void testEstablishRouteDirectProxy() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, false);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(execRuntime).connectEndpoint(Mockito.any());
        Mockito.when(execRuntime.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        exec.execute(request, scope, execChain);

        Mockito.verify(execRuntime).connectEndpoint(context);
        Mockito.verify(execRuntime, Mockito.never()).execute(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any());
    }

    @Test
    public void testEstablishRouteViaProxyTunnel() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(execRuntime).connectEndpoint(Mockito.any());
        Mockito.when(execRuntime.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(execRuntime.execute(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        exec.execute(request, scope, execChain);

        Mockito.verify(execRuntime).connectEndpoint(context);
        final ArgumentCaptor<ClassicHttpRequest> reqCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(execRuntime).execute(
                Mockito.anyString(),
                reqCaptor.capture(),
                Mockito.same(context));
        final HttpRequest connect = reqCaptor.getValue();
        Assertions.assertNotNull(connect);
        Assertions.assertEquals("CONNECT", connect.getMethod());
        Assertions.assertEquals(HttpVersion.HTTP_1_1, connect.getVersion());
        Assertions.assertEquals("foo:80", connect.getRequestUri());
    }

    @Test
    public void testEstablishRouteViaProxyTunnelUnexpectedResponse() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(101, "Lost");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(execRuntime).connectEndpoint(Mockito.any());
        Mockito.when(execRuntime.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(execRuntime.execute(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(HttpException.class, () ->
                exec.execute(request, scope, execChain));
    }

    @Test
    public void testEstablishRouteViaProxyTunnelFailure() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(500, "Boom");
        response.setEntity(new StringEntity("Ka-boom"));

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(execRuntime).connectEndpoint(Mockito.any());
        Mockito.when(execRuntime.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(execRuntime.execute(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final TunnelRefusedException exception = Assertions.assertThrows(TunnelRefusedException.class, () ->
                exec.execute(request, scope, execChain));
        Assertions.assertEquals("Ka-boom", exception.getResponseMessage());
        Mockito.verify(execRuntime, Mockito.atLeastOnce()).disconnectEndpoint();
        Mockito.verify(execRuntime).discardEndpoint();
    }

    @Test
    public void testEstablishRouteViaProxyTunnelRetryOnAuthChallengePersistentConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(407, "Huh?");
        response1.setHeader(HttpHeaders.PROXY_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final InputStream inStream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(inStream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");

        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(proxy), "user", "pass".toCharArray())
                .build());

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(execRuntime).connectEndpoint(Mockito.any());
        Mockito.when(execRuntime.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.any(),
                Mockito.any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(execRuntime.execute(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any())).thenReturn(response1, response2);

        Mockito.when(proxyAuthStrategy.select(
                Mockito.eq(ChallengeType.PROXY),
                Mockito.any(),
                Mockito.any())).thenReturn(Collections.singletonList(new BasicScheme()));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        exec.execute(request, scope, execChain);

        Mockito.verify(execRuntime).connectEndpoint(context);
        Mockito.verify(inStream1).close();
    }

    @Test
    public void testEstablishRouteViaProxyTunnelRetryOnAuthChallengeNonPersistentConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(407, "Huh?");
        response1.setHeader(HttpHeaders.PROXY_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final InputStream inStream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(inStream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");

        final CredentialsProvider credentialsProvider = CredentialsProviderBuilder.create()
                .add(new AuthScope(proxy), "user", "pass".toCharArray())
                .build();
        context.setCredentialsProvider(credentialsProvider);

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(execRuntime).connectEndpoint(Mockito.any());
        Mockito.when(execRuntime.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(execRuntime.execute(
                Mockito.anyString(),
                Mockito.any(),
                Mockito.any())).thenReturn(response1, response2);

        Mockito.when(proxyAuthStrategy.select(
                Mockito.eq(ChallengeType.PROXY),
                Mockito.any(),
                Mockito.any())).thenReturn(Collections.singletonList(new BasicScheme()));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        exec.execute(request, scope, execChain);

        Mockito.verify(execRuntime).connectEndpoint(context);
        Mockito.verify(inStream1, Mockito.never()).close();
        Mockito.verify(execRuntime, Mockito.atLeastOnce()).disconnectEndpoint();
    }

    @Test
    public void testEstablishRouteViaProxyTunnelMultipleHops() throws Exception {
        final HttpHost proxy1 = new HttpHost("this", 8888);
        final HttpHost proxy2 = new HttpHost("that", 8888);
        final HttpRoute route = new HttpRoute(target, null, new HttpHost[] {proxy1, proxy2},
                true, RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.LAYERED);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(execRuntime).connectEndpoint(Mockito.any());
        Mockito.when(execRuntime.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(HttpException.class, () ->
                exec.execute(request, scope, execChain));
    }

    static class ConnectionState {

        private boolean connected;

        public Answer<?> connectAnswer() {

            return invocationOnMock -> {
                connected = true;
                return null;
            };
        }

        public Answer<Boolean> isConnectedAnswer() {

            return invocationOnMock -> connected;

        }
    }

}
