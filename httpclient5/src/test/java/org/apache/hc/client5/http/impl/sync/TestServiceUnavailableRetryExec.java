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
package org.apache.hc.client5.http.impl.sync;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecRuntime;
import org.apache.hc.client5.http.sync.ServiceUnavailableRetryStrategy;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestServiceUnavailableRetryExec {

    @Mock
    private ServiceUnavailableRetryStrategy retryStrategy;
    @Mock
    private ExecChain chain;
    @Mock
    private ExecRuntime endpoint;

    private ServiceUnavailableRetryExec retryExec;
    private HttpHost target;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        retryExec = new ServiceUnavailableRetryExec(retryStrategy);
        target = new HttpHost("localhost", 80);
    }

    @Test
    public void testFundamentals() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.<ExecChain.Scope>any())).thenReturn(response);
        Mockito.when(retryStrategy.retryRequest(
                Mockito.<HttpResponse>any(),
                Mockito.anyInt(),
                Mockito.<HttpContext>any())).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(retryStrategy.getRetryInterval(
                Mockito.<HttpResponse>any(),
                Mockito.<HttpContext>any())).thenReturn(0L);

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        retryExec.execute(request, scope, chain);

        Mockito.verify(chain, Mockito.times(2)).proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.same(scope));
        Mockito.verify(response, Mockito.times(1)).close();
    }

    @Test(expected = RuntimeException.class)
    public void testStrategyRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenReturn(response);
        Mockito.doThrow(new RuntimeException("Ooopsie")).when(retryStrategy).retryRequest(
                Mockito.<HttpResponse>any(),
                Mockito.anyInt(),
                Mockito.<HttpContext>any());
        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        try {
            retryExec.execute(request, scope, chain);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
    }
}
