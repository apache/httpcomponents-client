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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.config.RedirectMethodPolicy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.support.ClassicResponseBuilder;
import org.apache.hc.core5.http.nio.AsyncDataConsumer;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.entity.StringAsyncEntityProducer;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Async redirect tests focused on preserving POST on 301/302 when
 * {@link RedirectMethodPolicy#PRESERVE_METHOD} is enabled.
 */
class TestAsyncRedirectExecTest {

    @Test
    void testPostMovedPermanentlyPreserveMethodAsync() throws Exception {
        // Arrange
        final HttpRoutePlanner routePlanner = Mockito.mock(HttpRoutePlanner.class);
        final AsyncRedirectExec redirectExec =
                new AsyncRedirectExec(routePlanner, new org.apache.hc.client5.http.impl.DefaultRedirectStrategy());
        final AsyncExecChain chain = Mockito.mock(AsyncExecChain.class);

        final HttpHost target = new HttpHost("localhost", 80);
        final HttpRoute route = new HttpRoute(target);

        final URI targetUri = new URI("http://localhost:80/stuff");
        final HttpRequest request = BasicRequestBuilder.post().setUri(targetUri).build();

        final AsyncEntityProducer entityProducer =
                new StringAsyncEntityProducer("stuff", org.apache.hc.core5.http.ContentType.TEXT_PLAIN);

        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setRedirectMethodPolicy(RedirectMethodPolicy.PRESERVE_METHOD)
                .build());

        final AsyncExecRuntime execRuntime = Mockito.mock(AsyncExecRuntime.class);
        final CancellableDependency dependency = Mockito.mock(CancellableDependency.class);
        final AtomicInteger execCount = new AtomicInteger(0);

        final AsyncExecChain.Scope scope = new AsyncExecChain.Scope(
                "test",
                route,
                request,
                dependency,           // not null
                context,
                execRuntime,
                /* scheduler */ null,
                execCount);           // AtomicInteger, not int

        final List<HttpRequest> seen = new ArrayList<>();
        final AtomicInteger call = new AtomicInteger(0);

        Mockito.doAnswer(inv -> {
            final HttpRequest req = inv.getArgument(0);
            final AsyncExecCallback cb = inv.getArgument(3);
            seen.add(req);

            if (call.getAndIncrement() == 0) {
                // First hop: 301 with Location to the same authority
                final HttpResponse r1 = ClassicResponseBuilder
                        .create(HttpStatus.SC_MOVED_PERMANENTLY)
                        .addHeader(HttpHeaders.LOCATION, "http://localhost:80/other-stuff")
                        .build();
                cb.handleResponse(r1, /* entity details */ null);
                cb.completed();
            } else {
                // Second hop: final 200 OK
                final HttpResponse r2 = ClassicResponseBuilder
                        .create(HttpStatus.SC_OK)
                        .build();
                cb.handleResponse(r2, null);
                cb.completed();
            }
            return null;
        }).when(chain).proceed(
                Mockito.any(HttpRequest.class),
                Mockito.any(AsyncEntityProducer.class),
                Mockito.any(AsyncExecChain.Scope.class),
                Mockito.any(AsyncExecCallback.class));

        // Act
        redirectExec.execute(request, entityProducer, scope, chain, new AsyncExecCallback() {
            @Override
            public AsyncDataConsumer handleResponse(final HttpResponse response,
                                                    final EntityDetails entityDetails) {
                return null; // no-op
            }

            @Override
            public void handleInformationResponse(final HttpResponse response) {
            }

            @Override
            public void completed() {
            }

            @Override
            public void failed(final Exception cause) {
                throw new AssertionError(cause);
            }
        });

        // Assert
        Assertions.assertEquals(2, seen.size(), "Expected two chain.proceed() calls");
        final HttpRequest first = seen.get(0);
        final HttpRequest second = seen.get(1);
        Assertions.assertEquals("POST", first.getMethod(), "first hop should be POST");
        Assertions.assertEquals("POST", second.getMethod(), "redirected hop should preserve POST");
    }
}
