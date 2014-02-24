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

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestServiceUnavailableRetryExec {

    @Mock
    private ClientExecChain requestExecutor;
    @Mock
    private ServiceUnavailableRetryStrategy retryStrategy;
    @Mock
    private HttpExecutionAware execAware;

    private ServiceUnavailableRetryExec retryExec;
    private HttpHost target;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        retryExec = new ServiceUnavailableRetryExec(requestExecutor, retryStrategy);
        target = new HttpHost("localhost", 80);
    }

    @Test
    public void testFundamentals() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);

        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);
        Mockito.when(retryStrategy.retryRequest(
                Mockito.<HttpResponse>any(),
                Mockito.anyInt(),
                Mockito.<HttpContext>any())).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(retryStrategy.getRetryInterval()).thenReturn(0L);

        retryExec.execute(route, request, context, execAware);

        Mockito.verify(requestExecutor, Mockito.times(2)).execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.same(context),
                Mockito.same(execAware));
        Mockito.verify(response, Mockito.times(1)).close();
    }

    @Test(expected = RuntimeException.class)
    public void testStrategyRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("/test"));
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);
        Mockito.doThrow(new RuntimeException("Ooopsie")).when(retryStrategy).retryRequest(
                Mockito.<HttpResponse>any(),
                Mockito.anyInt(),
                Mockito.<HttpContext>any());
        try {
            retryExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
    }
}
