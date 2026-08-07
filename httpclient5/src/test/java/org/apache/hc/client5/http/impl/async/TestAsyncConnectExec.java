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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.Cancellable;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.function.Resolver;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncClientExchangeHandler;
import org.apache.hc.core5.http.nio.RequestChannel;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestAsyncConnectExec {

    @Mock
    private HttpProcessor proxyHttpProcessor;
    @Mock
    private AuthenticationStrategy proxyAuthStrategy;
    @Mock
    private AsyncExecChain chain;
    @Mock
    private AsyncExecRuntime execRuntime;

    private HttpHost target;
    private HttpHost proxy;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        target = new HttpHost("https", "foo", 443);
        proxy = new HttpHost("bar", 8888);
    }

    /**
     * Drives the connect exec through a secure proxy tunnel and returns the {@code CONNECT} request
     * that the tunnelling exchange handler produces.
     */
    private HttpRequest tunnelConnectRequest(final AsyncConnectExec exec, final HttpRoute route) throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpRequest request = BasicRequestBuilder.get("https://foo/test").build();
        final CancellableDependency dependency = Mockito.mock(CancellableDependency.class);
        final AsyncExecChain.Scope scope = new AsyncExecChain.Scope(
                "test", route, request, dependency, context, execRuntime, null, new AtomicInteger(1));

        Mockito.when(execRuntime.isEndpointAcquired()).thenReturn(false);
        Mockito.when(execRuntime.isEndpointConnected()).thenReturn(false);
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(4, FutureCallback.class).completed(execRuntime);
            return Mockito.mock(Cancellable.class);
        }).when(execRuntime).acquireEndpoint(
                Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.doAnswer(invocation -> {
            invocation.getArgument(1, FutureCallback.class).completed(execRuntime);
            return Mockito.mock(Cancellable.class);
        }).when(execRuntime).connectEndpoint(Mockito.any(), Mockito.any());

        final AtomicReference<AsyncClientExchangeHandler> handlerRef = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1, AsyncClientExchangeHandler.class));
            return Mockito.mock(Cancellable.class);
        }).when(execRuntime).execute(Mockito.anyString(), Mockito.any(), Mockito.any());

        exec.execute(request, null, scope, chain, Mockito.mock(AsyncExecCallback.class));

        final AsyncClientExchangeHandler handler = handlerRef.get();
        Assertions.assertNotNull(handler, "CONNECT exchange handler must have been submitted for execution");

        final AtomicReference<HttpRequest> connectRef = new AtomicReference<>();
        final RequestChannel requestChannel = Mockito.mock(RequestChannel.class);
        Mockito.doAnswer(invocation -> {
            connectRef.set(invocation.getArgument(0, HttpRequest.class));
            return null;
        }).when(requestChannel).sendRequest(Mockito.any(), Mockito.any(), Mockito.any());
        handler.produceRequest(requestChannel, context);
        return connectRef.get();
    }

    @Test
    void testEstablishRouteViaProxyTunnelAddsAlpnHeader() throws Exception {
        // No per-host TlsConfig resolver: the effective policy is NEGOTIATE, so the tunnel's TLS
        // layer offers both protocols and the ALPN header advertises the same set.
        final AsyncConnectExec exec = new AsyncConnectExec(proxyHttpProcessor, proxyAuthStrategy, null, true);
        final HttpRoute route = new HttpRoute(target, null, proxy, true);

        final HttpRequest connect = tunnelConnectRequest(exec, route);

        Assertions.assertEquals("CONNECT", connect.getMethod());
        final Header h = connect.getFirstHeader(HttpHeaders.ALPN);
        Assertions.assertNotNull(h, "ALPN header must be present");
        Assertions.assertEquals("h2, http%2F1.1", h.getValue());
    }

    @Test
    void testEstablishRouteViaProxyTunnelAlpnHeaderReflectsVersionPolicy() throws Exception {
        // A per-host FORCE_HTTP_1 policy must be reflected verbatim: only http/1.1 is advertised,
        // so the header can never contradict the protocol negotiated inside the tunnel.
        final Resolver<HttpHost, TlsConfig> tlsConfigResolver =
                host -> TlsConfig.custom().setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1).build();
        final AsyncConnectExec exec = new AsyncConnectExec(
                proxyHttpProcessor, proxyAuthStrategy, null, true, tlsConfigResolver);
        final HttpRoute route = new HttpRoute(target, null, proxy, true);

        final HttpRequest connect = tunnelConnectRequest(exec, route);

        final Header h = connect.getFirstHeader(HttpHeaders.ALPN);
        Assertions.assertNotNull(h, "ALPN header must be present");
        Assertions.assertEquals("http%2F1.1", h.getValue());
    }

}
