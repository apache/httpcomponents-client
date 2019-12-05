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
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@SuppressWarnings({"boxing","static-access"}) // test code
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
        MockitoAnnotations.initMocks(this);
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
                Mockito.<ExecChain.Scope>any())).thenReturn(response);
        Mockito.when(retryStrategy.retryRequest(
                Mockito.<HttpResponse>any(),
                Mockito.anyInt(),
                Mockito.<HttpContext>any())).thenReturn(Boolean.TRUE, Boolean.FALSE);
        Mockito.when(retryStrategy.getRetryInterval(
                Mockito.<HttpResponse>any(),
                Mockito.anyInt(),
                Mockito.<HttpContext>any())).thenReturn(TimeValue.ZERO_MILLISECONDS);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
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
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        try {
            retryExec.execute(request, scope, chain);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
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
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenReturn(response);
        Mockito.when(retryStrategy.retryRequest(
                Mockito.<HttpResponse>any(),
                Mockito.anyInt(),
                Mockito.<HttpContext>any())).thenReturn(Boolean.TRUE, Boolean.FALSE);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = retryExec.execute(request, scope, chain);

        Assert.assertSame(response, finalResponse);
        Mockito.verify(response, Mockito.times(0)).close();
    }

    @Test(expected = IOException.class)
    public void testFundamentals2() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet originalRequest = new HttpGet("/test");
        originalRequest.addHeader("header", "this");
        originalRequest.addHeader("header", "that");
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenAnswer(new Answer<Object>() {

            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final Object[] args = invocationOnMock.getArguments();
                final ClassicHttpRequest wrapper = (ClassicHttpRequest) args[0];
                final Header[] headers = wrapper.getHeaders();
                Assert.assertEquals(2, headers.length);
                Assert.assertEquals("this", headers[0].getValue());
                Assert.assertEquals("that", headers[1].getValue());
                wrapper.addHeader("Cookie", "monster");
                throw new IOException("Ka-boom");
            }

        });
        Mockito.when(retryStrategy.retryRequest(
                Mockito.<HttpRequest>any(),
                Mockito.<IOException>any(),
                Mockito.eq(1),
                Mockito.<HttpContext>any())).thenReturn(Boolean.TRUE);
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, originalRequest, endpoint, context);
        final ClassicHttpRequest request = ClassicRequestCopier.INSTANCE.copy(originalRequest);
        try {
            retryExec.execute(request, scope, chain);
        } catch (final IOException ex) {
            Mockito.verify(chain, Mockito.times(2)).proceed(
                    Mockito.<ClassicHttpRequest>any(),
                    Mockito.same(scope));
            throw ex;
        }
    }


    @Test(expected = IOException.class)
    public void testAbortedRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet originalRequest = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenThrow(new IOException("Ka-boom"));
        Mockito.when(endpoint.isExecutionAborted()).thenReturn(true);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, originalRequest, endpoint, context);
        final ClassicHttpRequest request = ClassicRequestCopier.INSTANCE.copy(originalRequest);
        try {
            retryExec.execute(request, scope, chain);
        } catch (final IOException ex) {
            Mockito.verify(chain, Mockito.times(1)).proceed(
                    Mockito.same(request),
                    Mockito.same(scope));
            Mockito.verify(retryStrategy, Mockito.never()).retryRequest(
                    Mockito.<HttpRequest>any(),
                    Mockito.<IOException>any(),
                    Mockito.anyInt(),
                    Mockito.<HttpContext>any());

            throw ex;
        }
    }

    @Test(expected = IOException.class)
    public void testNonRepeatableRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpPost originalRequest = new HttpPost("/test");
        originalRequest.setEntity(EntityBuilder.create()
                .setStream(new ByteArrayInputStream(new byte[]{}))
                .build());
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(chain.proceed(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any())).thenAnswer(new Answer<Object>() {

            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final Object[] args = invocationOnMock.getArguments();
                final ClassicHttpRequest req = (ClassicHttpRequest) args[0];
                req.getEntity().writeTo(new ByteArrayOutputStream());
                throw new IOException("Ka-boom");
            }

        });
        Mockito.when(retryStrategy.retryRequest(
                Mockito.<HttpRequest>any(),
                Mockito.<IOException>any(),
                Mockito.eq(1),
                Mockito.<HttpContext>any())).thenReturn(Boolean.TRUE);
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, originalRequest, endpoint, context);
        final ClassicHttpRequest request = ClassicRequestCopier.INSTANCE.copy(originalRequest);
        try {
            retryExec.execute(request, scope, chain);
        } catch (final IOException ex) {
            Mockito.verify(chain, Mockito.times(1)).proceed(
                    Mockito.same(request),
                    Mockito.same(scope));

            throw ex;
        }
    }

}
