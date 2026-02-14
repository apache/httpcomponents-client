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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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
        MockitoAnnotations.openMocks(this);
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
}
