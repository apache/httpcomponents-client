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

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.UnsupportedSchemeException;
import org.apache.hc.client5.http.async.AsyncExecCallback;
import org.apache.hc.client5.http.async.AsyncExecChain;
import org.apache.hc.client5.http.async.AsyncExecRuntime;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.CancellableDependency;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.support.BasicRequestBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestTlsRequiredAsyncExec {

    @Mock
    private AsyncExecChain chain;
    @Mock
    private AsyncExecCallback callback;
    @Mock
    private AsyncExecRuntime execRuntime;
    @Mock
    private AsyncEntityProducer entityProducer;

    private AsyncExecChain.Scope scope(final HttpRoute route, final HttpRequest request) {
        return new AsyncExecChain.Scope(
                "test", route, request, Mockito.mock(CancellableDependency.class),
                HttpClientContext.create(), execRuntime, null, new AtomicInteger(1));
    }

    @Test
    void cleartextRouteIsRejectedWithoutProceeding() throws Exception {
        final HttpRequest request = BasicRequestBuilder.get("http://localhost/").build();
        final AsyncExecChain.Scope scope = scope(new HttpRoute(new HttpHost("http", "localhost", 80)), request);

        new TlsRequiredAsyncExec().execute(request, entityProducer, scope, chain, callback);

        Mockito.verify(callback, Mockito.times(1)).failed(Mockito.any(UnsupportedSchemeException.class));
        Mockito.verify(chain, Mockito.never()).proceed(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void secureRouteProceeds() throws Exception {
        final HttpRequest request = BasicRequestBuilder.get("https://localhost/").build();
        final AsyncExecChain.Scope scope = scope(new HttpRoute(new HttpHost("https", "localhost", 443), null, true), request);

        new TlsRequiredAsyncExec().execute(request, entityProducer, scope, chain, callback);

        Mockito.verify(chain, Mockito.times(1)).proceed(request, entityProducer, scope, callback);
        Mockito.verify(callback, Mockito.never()).failed(Mockito.any());
    }

}
