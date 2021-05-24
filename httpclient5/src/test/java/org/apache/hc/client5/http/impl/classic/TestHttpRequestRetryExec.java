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
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings({"boxing","static-access"}) // test code
@RunWith(MockitoJUnitRunner.class)
public class TestHttpRequestRetryExec {

    @Mock
    private HttpRequestRetryStrategy retryStrategy;
    @Mock
    private ExecChain chain;
    @Mock
    private ExecRuntime endpoint;

    private HttpRequestRetryExec retryExec;
    private HttpHost target;

    @Before
    public void setup() throws Exception {
        retryExec = new HttpRequestRetryExec(retryStrategy);
        target = new HttpHost("localhost", 80);
    }


    @Test
    public void testFundamentals1() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(response);
        Mockito.when(retryStrategy.retryRequest(
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any())).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(retryStrategy.getRetryInterval(
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any())).thenReturn(TimeValue.ZERO_MILLISECONDS);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        retryExec.execute(request, scope, chain);

        Mockito.verify(chain, Mockito.times(2)).proceed(
                Mockito.any(),
                Mockito.same(scope));
        Mockito.verify(response, Mockito.times(1)).close();
    }

    @Test
    public void testRetryIntervalGreaterResponseTimeout() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom()
                .setResponseTimeout(Timeout.ofSeconds(3))
                .build());

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(response);
        Mockito.when(retryStrategy.retryRequest(
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any())).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(retryStrategy.getRetryInterval(
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any())).thenReturn(TimeValue.ofSeconds(5));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        retryExec.execute(request, scope, chain);

        Mockito.verify(chain, Mockito.times(1)).proceed(
                Mockito.any(),
                Mockito.same(scope));
        Mockito.verify(response, Mockito.times(0)).close();
    }

    @Test
    public void testRetryIntervalResponseTimeoutNull() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom()
                .setResponseTimeout(null)
                .build());

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(response);
        Mockito.when(retryStrategy.retryRequest(
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any())).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(retryStrategy.getRetryInterval(
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any())).thenReturn(TimeValue.ofSeconds(1));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        retryExec.execute(request, scope, chain);

        Mockito.verify(chain, Mockito.times(2)).proceed(
                Mockito.any(),
                Mockito.same(scope));
        Mockito.verify(response, Mockito.times(1)).close();
    }

    @Test
    public void testStrategyRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(chain.proceed(
                Mockito.any(),
                Mockito.any())).thenReturn(response);
        Mockito.doThrow(new RuntimeException("Ooopsie")).when(retryStrategy).retryRequest(
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        Assert.assertThrows(RuntimeException.class, () ->
                retryExec.execute(request, scope, chain));
        Mockito.verify(response).close();
    }

    @Test
    public void testNonRepeatableEntityResponseReturnedImmediately() throws Exception {
        final HttpRoute route = new HttpRoute(target);

        final HttpPost request = new HttpPost("/test");
        request.setEntity(EntityBuilder.create()
                .setStream(new ByteArrayInputStream(new byte[]{}))
                .build());
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(chain.proceed(
                Mockito.any(),
                Mockito.any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = retryExec.execute(request, scope, chain);

        Assert.assertSame(response, finalResponse);
        Mockito.verify(response, Mockito.times(0)).close();
    }

    @Test
    public void testFundamentals2() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet originalRequest = new HttpGet("/test");
        originalRequest.addHeader("header", "this");
        originalRequest.addHeader("header", "that");
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(chain.proceed(
                Mockito.any(),
                Mockito.any())).thenAnswer(invocationOnMock -> {
                    final Object[] args = invocationOnMock.getArguments();
                    final ClassicHttpRequest wrapper = (ClassicHttpRequest) args[0];
                    final Header[] headers = wrapper.getHeaders();
                    Assert.assertEquals(2, headers.length);
                    Assert.assertEquals("this", headers[0].getValue());
                    Assert.assertEquals("that", headers[1].getValue());
                    wrapper.addHeader("Cookie", "monster");
                    throw new IOException("Ka-boom");
                });
        Mockito.when(retryStrategy.retryRequest(
                Mockito.any(),
                Mockito.any(),
                Mockito.eq(1),
                Mockito.any())).thenReturn(Boolean.TRUE);
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, originalRequest, endpoint, context);
        final ClassicHttpRequest request = ClassicRequestBuilder.copy(originalRequest).build();
        Assert.assertThrows(IOException.class, () ->
                retryExec.execute(request, scope, chain));
        Mockito.verify(chain, Mockito.times(2)).proceed(
                Mockito.any(),
                Mockito.same(scope));
    }


    @Test
    public void testAbortedRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet originalRequest = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(chain.proceed(
                Mockito.any(),
                Mockito.any())).thenThrow(new IOException("Ka-boom"));
        Mockito.when(endpoint.isExecutionAborted()).thenReturn(true);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, originalRequest, endpoint, context);
        final ClassicHttpRequest request = ClassicRequestBuilder.copy(originalRequest).build();
        Assert.assertThrows(IOException.class, () ->
                retryExec.execute(request, scope, chain));
        Mockito.verify(chain, Mockito.times(1)).proceed(
                Mockito.same(request),
                Mockito.same(scope));
        Mockito.verify(retryStrategy, Mockito.never()).retryRequest(
                Mockito.any(),
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.any());
    }

    @Test
    public void testNonRepeatableRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpPost originalRequest = new HttpPost("/test");
        originalRequest.setEntity(EntityBuilder.create()
                .setStream(new ByteArrayInputStream(new byte[]{}))
                .build());
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(chain.proceed(
                Mockito.any(),
                Mockito.any())).thenAnswer(invocationOnMock -> {
                    final Object[] args = invocationOnMock.getArguments();
                    final ClassicHttpRequest req = (ClassicHttpRequest) args[0];
                    req.getEntity().writeTo(new ByteArrayOutputStream());
                    throw new IOException("Ka-boom");
                });
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, originalRequest, endpoint, context);
        final ClassicHttpRequest request = ClassicRequestBuilder.copy(originalRequest).build();
        Assert.assertThrows(IOException.class, () ->
                retryExec.execute(request, scope, chain));
        Mockito.verify(chain, Mockito.times(1)).proceed(
                Mockito.same(request),
                Mockito.same(scope));
    }

}
