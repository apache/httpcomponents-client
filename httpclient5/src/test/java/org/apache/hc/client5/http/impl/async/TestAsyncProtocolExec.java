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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.message.BasicHttpResponse;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestAsyncProtocolExec {

    @Mock
    private AuthenticationStrategy targetAuthStrategy;
    @Mock
    private AuthenticationStrategy proxyAuthStrategy;
    @Mock
    private AsyncExecChain chain;
    @Mock
    private AsyncExecRuntime execRuntime;

    private AsyncProtocolExec protocolExec;
    private HttpHost target;

    @BeforeEach
    void setup() {
        protocolExec = new AsyncProtocolExec(targetAuthStrategy, proxyAuthStrategy, null, true);
        target = new HttpHost("http", "foo", 80);
    }

    @Test
    void testAuthExchangePathPrefixRestoredAfterChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());

        Mockito.when(targetAuthStrategy.select(
                        Mockito.eq(ChallengeType.TARGET),
                        Mockito.any(),
                        Mockito.<HttpClientContext>any()))
                .thenReturn(Collections.singletonList(new BasicScheme()));

        Mockito.when(execRuntime.isEndpointConnected()).thenReturn(true);

        final AtomicInteger proceedCount = new AtomicInteger(0);

        Mockito.doAnswer(invocation -> {
            final AsyncExecCallback cb = invocation.getArgument(3, AsyncExecCallback.class);
            final int i = proceedCount.getAndIncrement();

            if (i == 0) {
                final HttpResponse resp1 = new BasicHttpResponse(401, "Huh?");
                resp1.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
                cb.handleResponse(resp1, (EntityDetails) null);
                cb.completed();
            } else {
                final HttpResponse resp2 = new BasicHttpResponse(200, "OK");
                cb.handleResponse(resp2, (EntityDetails) null);
                cb.completed();
            }
            return null;
        }).when(chain).proceed(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        final HttpRequest request = BasicRequestBuilder.get("http://foo/blah/a").build();

        final CancellableDependency dependency = Mockito.mock(CancellableDependency.class);
        final AsyncExecChain.Scope scope = new AsyncExecChain.Scope(
                "test",
                route,
                request,
                dependency,
                context,
                execRuntime,
                null,
                new AtomicInteger(1));

        final AsyncExecCallback asyncCallback = new AsyncExecCallback() {
            @Override
            public AsyncDataConsumer handleResponse(final HttpResponse response, final EntityDetails entityDetails)
                    throws IOException {
                return Mockito.mock(AsyncDataConsumer.class);
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) {
            }

            @Override
            public void completed() {
            }

            @Override
            public void failed(final Exception cause) {
                Assertions.fail(cause);
            }
        };

        protocolExec.execute(request, (AsyncEntityProducer) null, scope, chain, asyncCallback);

        Assertions.assertEquals(2, proceedCount.get());

        final AuthExchange authExchange = context.getAuthExchange(target);
        Assertions.assertEquals("/blah/", authExchange.getPathPrefix());
    }

    private static final class RecordingCallback implements AsyncExecCallback {

        private final List<Integer> codes = new ArrayList<>();
        private final AtomicInteger completed = new AtomicInteger(0);

        @Override
        public AsyncDataConsumer handleResponse(final HttpResponse response, final EntityDetails entityDetails) {
            codes.add(response.getCode());
            return Mockito.mock(AsyncDataConsumer.class);
        }

        @Override
        public void handleInformationResponse(final HttpResponse response) {
        }

        @Override
        public void completed() {
            completed.incrementAndGet();
        }

        @Override
        public void failed(final Exception cause) {
            Assertions.fail(cause);
        }

    }

    private void stubChain(final AtomicInteger proceedCount, final List<HttpRequest> requests, final int... codes)
            throws Exception {
        Mockito.doAnswer(invocation -> {
            requests.add(invocation.getArgument(0, HttpRequest.class));
            final AsyncExecCallback cb = invocation.getArgument(3, AsyncExecCallback.class);
            final int code = codes[Math.min(proceedCount.getAndIncrement(), codes.length - 1)];
            final HttpResponse resp = new BasicHttpResponse(code, "Huh?");
            if (code == 407) {
                resp.setHeader(HttpHeaders.PROXY_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
            }
            cb.handleResponse(resp, (EntityDetails) null);
            cb.completed();
            return null;
        }).when(chain).proceed(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void testProxyChallengeThroughTunnelWithAuthCacheIsReturnedToCaller() throws Exception {
        final AsyncProtocolExec cachingExec = new AsyncProtocolExec(targetAuthStrategy, proxyAuthStrategy, null, false);
        final HttpHost httpsTarget = new HttpHost("https", "foo", 443);
        final HttpHost proxy = new HttpHost("http", "proxy", 8080);
        final HttpRoute route = new HttpRoute(httpsTarget, null, proxy, true,
                RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.LAYERED);
        final BasicAuthCache authCache = new BasicAuthCache();
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(authCache);
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(proxy), "proxyuser", "proxypass".toCharArray())
                .build());
        Mockito.when(execRuntime.isEndpointConnected()).thenReturn(true);
        final AtomicInteger proceedCount = new AtomicInteger(0);
        final List<HttpRequest> requests = new ArrayList<>();
        stubChain(proceedCount, requests, 407);

        final HttpRequest request = BasicRequestBuilder.get("https://foo/test").build();
        final AsyncExecChain.Scope scope = new AsyncExecChain.Scope("test", route, request,
                Mockito.mock(CancellableDependency.class), context, execRuntime, null, new AtomicInteger(1));
        final RecordingCallback callback = new RecordingCallback();

        cachingExec.execute(request, (AsyncEntityProducer) null, scope, chain, callback);

        Assertions.assertEquals(Collections.singletonList(407), callback.codes);
        Assertions.assertEquals(1, callback.completed.get());
        Assertions.assertEquals(1, proceedCount.get());
        Assertions.assertNull(requests.get(0).getFirstHeader(HttpHeaders.PROXY_AUTHORIZATION));
        Mockito.verifyNoInteractions(proxyAuthStrategy);
        Assertions.assertEquals(AuthExchange.State.UNCHALLENGED, context.getAuthExchange(proxy).getState());
        Assertions.assertNull(authCache.get(proxy));
    }

    @Test
    void testProxyChallengeOnPlainRouteStillAuthenticates() throws Exception {
        final HttpHost proxy = new HttpHost("http", "proxy", 8080);
        final HttpRoute route = new HttpRoute(target, null, proxy, false);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(proxy), "proxyuser", "proxypass".toCharArray())
                .build());
        Mockito.when(proxyAuthStrategy.select(
                        Mockito.eq(ChallengeType.PROXY),
                        Mockito.any(),
                        Mockito.<HttpClientContext>any()))
                .thenReturn(Collections.singletonList(new BasicScheme()));
        Mockito.when(execRuntime.isEndpointConnected()).thenReturn(true);
        final AtomicInteger proceedCount = new AtomicInteger(0);
        final List<HttpRequest> requests = new ArrayList<>();
        stubChain(proceedCount, requests, 407, 200);

        final HttpRequest request = BasicRequestBuilder.get("http://foo/test").build();
        final AsyncExecChain.Scope scope = new AsyncExecChain.Scope("test", route, request,
                Mockito.mock(CancellableDependency.class), context, execRuntime, null, new AtomicInteger(1));
        final RecordingCallback callback = new RecordingCallback();

        protocolExec.execute(request, (AsyncEntityProducer) null, scope, chain, callback);

        Assertions.assertEquals(2, proceedCount.get());
        Assertions.assertEquals(Collections.singletonList(200), callback.codes);
        Assertions.assertNotNull(requests.get(1).getFirstHeader(HttpHeaders.PROXY_AUTHORIZATION));
    }
}
