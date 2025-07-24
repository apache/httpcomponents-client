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
package org.apache.hc.client5.http.impl.async;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.impl.DefaultAuthenticationStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRouteDirector;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AsyncConnectExecTest {

    @Mock
    private HttpProcessor proxyHttpProcessor;

    private AuthenticationStrategy proxyAuthStrategy;

    @Mock
    private SchemePortResolver schemePortResolver;

    @Mock
    private AsyncExecRuntime execRuntime;

    @Mock
    private AsyncExecChain chain;

    @Mock
    private AsyncExecCallback callback;

    @Mock
    private AsyncExecChain.Scheduler scheduler;

    @Mock
    private CancellableDependency cancellableDependency;

    @Mock
    private HttpRouteDirector mockRouteDirector;

    @Mock
    private Cancellable mockCancellable;

    @Mock
    private HttpResponse mockResponse;

    private AsyncConnectExec connectExec;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        proxyAuthStrategy = new DefaultAuthenticationStrategy();
        connectExec = new AsyncConnectExec(proxyHttpProcessor, proxyAuthStrategy, schemePortResolver, false);

        // Use reflection to set the private routeDirector to mock
        final Field routeDirectorField = AsyncConnectExec.class.getDeclaredField("routeDirector");
        routeDirectorField.setAccessible(true);
        routeDirectorField.set(connectExec, mockRouteDirector);
    }

    private AsyncExecChain.Scope createScope(final HttpRoute route) {
        final HttpRequest originalRequest = new BasicHttpRequest("GET", "/");
        return new AsyncExecChain.Scope(
                "test-exchange-id",
                route,
                originalRequest,
                cancellableDependency,
                HttpClientContext.create(),
                execRuntime,
                scheduler,
                new AtomicInteger(1)
        );
    }

    @Test
    void testProxyChainTunnel() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpHost proxy1 = new HttpHost("proxy1", 80);
        final HttpHost proxy2 = new HttpHost("proxy2", 80);
        final HttpRoute route = new HttpRoute(target, null, new HttpHost[]{proxy1, proxy2}, false, RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.PLAIN);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false, true, true, true, true, true);

        // Mock route director to simulate steps: CONNECT_PROXY -> TUNNEL_PROXY -> TUNNEL_TARGET -> COMPLETE
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.CONNECT_PROXY)
                .thenReturn(HttpRouteDirector.TUNNEL_PROXY)
                .thenReturn(HttpRouteDirector.TUNNEL_TARGET)
                .thenReturn(HttpRouteDirector.COMPLETE);

        // Mock connectEndpoint to simulate successful connection
        doAnswer(invocation -> {
            cancellableDependency.setDependency(mockCancellable);
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).connectEndpoint(any(), any());

        // Mock execute for createTunnel to simulate successful tunnel (HTTP 200 response)
        doAnswer(invocation -> {
            final AsyncClientExchangeHandler handler = invocation.getArgument(1);
            handler.consumeResponse(mockResponse, null, scope.clientContext);
            handler.streamEnd(null);
            return mockCancellable;
        }).when(execRuntime).execute(anyString(), any(), any());

        // Mock response for tunnel success
        when(mockResponse.getCode()).thenReturn(HttpStatus.SC_OK);
        when(mockResponse.containsHeader("Proxy-Authenticate")).thenReturn(false);

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify that the tunnel was created twice (for proxy2 and target)
        verify(execRuntime, times(2)).execute(anyString(), any(), any());

        // Verify that the chain proceeds exactly three times
        verify(chain, times(3)).proceed(any(), any(), eq(scope), eq(callback));

        // Ensure no failure for unsupported proxy chains
        verify(callback, never()).failed(argThat(ex -> ex instanceof HttpException &&
                ex.getMessage().contains("Proxy chains are not supported")));
    }

    @Test
    void testConnectTarget() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpRoute route = new HttpRoute(target);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false, true);

        // Mock route director to simulate CONNECT_TARGET -> COMPLETE
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.CONNECT_TARGET)
                .thenReturn(HttpRouteDirector.COMPLETE);

        // Mock connectEndpoint to simulate successful connection
        doAnswer(invocation -> {
            cancellableDependency.setDependency(mockCancellable);
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).connectEndpoint(any(), any());

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify connectEndpoint called and proceed
        verify(execRuntime).connectEndpoint(any(), any());
        verify(chain).proceed(any(), any(), eq(scope), eq(callback));
    }

    @Test
    void testConnectProxy() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpHost proxy = new HttpHost("proxy", 80);
        final HttpRoute route = new HttpRoute(target, null, proxy, false);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false, true);

        // Mock route director to simulate CONNECT_PROXY -> COMPLETE
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.CONNECT_PROXY)
                .thenReturn(HttpRouteDirector.COMPLETE);

        // Mock connectEndpoint to simulate successful connection
        doAnswer(invocation -> {
            cancellableDependency.setDependency(mockCancellable);
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).connectEndpoint(any(), any());

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify connectEndpoint called and proceed
        verify(execRuntime).connectEndpoint(any(), any());
        verify(chain).proceed(any(), any(), eq(scope), eq(callback));
    }

    @Test
    void testTunnelTarget() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpHost proxy = new HttpHost("proxy", 80);
        final HttpRoute route = new HttpRoute(target, null, proxy, false, RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.PLAIN);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false, true, true);

        // Mock route director to simulate CONNECT_PROXY -> TUNNEL_TARGET -> COMPLETE
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.CONNECT_PROXY)
                .thenReturn(HttpRouteDirector.TUNNEL_TARGET)
                .thenReturn(HttpRouteDirector.COMPLETE);

        // Mock connectEndpoint to simulate successful connection
        doAnswer(invocation -> {
            cancellableDependency.setDependency(mockCancellable);
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).connectEndpoint(any(), any());

        // Mock execute for createTunnel to simulate successful tunnel
        doAnswer(invocation -> {
            final AsyncClientExchangeHandler handler = invocation.getArgument(1);
            handler.consumeResponse(mockResponse, null, scope.clientContext);
            handler.streamEnd(null);
            return mockCancellable;
        }).when(execRuntime).execute(anyString(), any(), any());

        // Mock response for tunnel success
        when(mockResponse.getCode()).thenReturn(HttpStatus.SC_OK);

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify tunnel created and proceed
        verify(execRuntime).execute(anyString(), any(), any());
        verify(chain, times(2)).proceed(any(), any(), eq(scope), eq(callback));
    }

    @Test
    void testLayerProtocol() throws Exception {
        final HttpHost target = new HttpHost("https", "target", 443);
        final HttpRoute route = new HttpRoute(target);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false, true, true);

        // Mock route director to simulate CONNECT_TARGET -> LAYER_PROTOCOL -> COMPLETE
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.CONNECT_TARGET)
                .thenReturn(HttpRouteDirector.LAYER_PROTOCOL)
                .thenReturn(HttpRouteDirector.COMPLETE);

        // Mock connectEndpoint to simulate successful connection
        doAnswer(invocation -> {
            cancellableDependency.setDependency(mockCancellable);
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).connectEndpoint(any(), any());

        // Mock upgradeTls to simulate successful upgrade
        doAnswer(invocation -> {
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).upgradeTls(any(), any());

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify upgradeTls called and proceed
        verify(execRuntime).upgradeTls(any(), any());
        verify(chain, times(1)).proceed(any(), any(), eq(scope), eq(callback));
    }

    @Test
    void testUnreachable() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpRoute route = new HttpRoute(target);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false);

        // Mock route director to simulate UNREACHABLE
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.UNREACHABLE);

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify failure
        verify(callback).failed(any(HttpException.class));
    }

    @Test
    void testComplete() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpRoute route = new HttpRoute(target);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(true);

        // Mock route director to simulate COMPLETE
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.COMPLETE);

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify proceed called
        verify(chain).proceed(any(), any(), eq(scope), eq(callback));
    }

    @Test
    void testTunnelRefused() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpHost proxy = new HttpHost("proxy", 80);
        final HttpRoute route = new HttpRoute(target, null, proxy, false, RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.PLAIN);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false, true);

        // Mock route director to simulate CONNECT_PROXY -> TUNNEL_TARGET
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.CONNECT_PROXY)
                .thenReturn(HttpRouteDirector.TUNNEL_TARGET);

        // Mock connectEndpoint to simulate successful connection
        doAnswer(invocation -> {
            cancellableDependency.setDependency(mockCancellable);
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).connectEndpoint(any(), any());

        // Mock execute for createTunnel to simulate refusal (403)
        doAnswer(invocation -> {
            final AsyncClientExchangeHandler handler = invocation.getArgument(1);
            handler.consumeResponse(mockResponse, null, scope.clientContext);
            handler.streamEnd(null);
            return mockCancellable;
        }).when(execRuntime).execute(anyString(), any(), any());

        // Mock response for tunnel refused
        when(mockResponse.getCode()).thenReturn(HttpStatus.SC_FORBIDDEN);

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify completed called (for tunnel refused)
        verify(callback).completed();
    }

    @Test
    void testHttpsProxyChain() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpHost proxy1 = new HttpHost("proxy1", 80);
        final HttpHost proxy2 = new HttpHost("https", "proxy2", 443);
        final HttpRoute route = new HttpRoute(target, null, new HttpHost[]{proxy1, proxy2}, false, RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.PLAIN);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        // Mock runtime behavior
        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false, true, true, true, true, true);

        // Mock route director to simulate CONNECT_PROXY -> TUNNEL_PROXY -> TUNNEL_TARGET -> COMPLETE
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.CONNECT_PROXY)
                .thenReturn(HttpRouteDirector.TUNNEL_PROXY)
                .thenReturn(HttpRouteDirector.TUNNEL_TARGET)
                .thenReturn(HttpRouteDirector.COMPLETE);

        // Mock connectEndpoint to simulate successful connection
        doAnswer(invocation -> {
            cancellableDependency.setDependency(mockCancellable);
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).connectEndpoint(any(), any());

        // Mock execute for createTunnel to simulate successful tunnel
        doAnswer(invocation -> {
            final AsyncClientExchangeHandler handler = invocation.getArgument(1);
            handler.consumeResponse(mockResponse, null, scope.clientContext);
            handler.streamEnd(null);
            return mockCancellable;
        }).when(execRuntime).execute(anyString(), any(), any());

        // Mock upgradeTls for HTTPS proxy
        doAnswer(invocation -> {
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).upgradeTls(any(), any());

        // Mock response for tunnel success
        when(mockResponse.getCode()).thenReturn(HttpStatus.SC_OK);
        when(mockResponse.containsHeader("Proxy-Authenticate")).thenReturn(false);

        // Execute the method under test
        connectExec.execute(request, entityProducer, scope, chain, callback);

        // Verify upgradeTls called for HTTPS proxy
        verify(chain, times(3)).proceed(any(), any(), eq(scope), eq(callback));
    }

    @Test
    void testTunnelFailure() throws Exception {
        final HttpHost target = new HttpHost("target", 80);
        final HttpHost proxy = new HttpHost("proxy", 80);
        final HttpRoute route = new HttpRoute(target, null, proxy, false, RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.PLAIN);
        final AsyncExecChain.Scope scope = createScope(route);

        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final AsyncEntityProducer entityProducer = null;

        when(execRuntime.isEndpointAcquired()).thenReturn(true);
        when(execRuntime.isEndpointConnected()).thenReturn(false, true);
        when(mockRouteDirector.nextStep(any(), any()))
                .thenReturn(HttpRouteDirector.CONNECT_PROXY)
                .thenReturn(HttpRouteDirector.TUNNEL_TARGET);
        doAnswer(invocation -> {
            cancellableDependency.setDependency(mockCancellable);
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return mockCancellable;
        }).when(execRuntime).connectEndpoint(any(), any());

        doAnswer(invocation -> {
            final AsyncClientExchangeHandler handler = invocation.getArgument(1);
            // Simulate the handler creation and immediate failure
            handler.failed(new IOException("Connection failed"));
            return mockCancellable;
        }).when(execRuntime).execute(anyString(), any(), any());

        connectExec.execute(request, entityProducer, scope, chain, callback);
        verify(callback).failed(any(IOException.class));
    }
}