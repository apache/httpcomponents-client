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

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.NonRepeatableRequestException;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestRetryExec {

    @Mock
    private ClientExecChain requestExecutor;
    @Mock
    private HttpRequestRetryHandler retryHandler;
    @Mock
    private HttpExecutionAware execAware;

    private RetryExec retryExec;
    private HttpHost target;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        retryExec = new RetryExec(requestExecutor, retryHandler);
        target = new HttpHost("localhost", 80);
    }

    @Test(expected = IOException.class)
    public void testFundamentals() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        get.addHeader("header", "this");
        get.addHeader("header", "that");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenAnswer(new Answer<Object>() {

            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final Object[] args = invocationOnMock.getArguments();
                final HttpRequestWrapper wrapper = (HttpRequestWrapper) args[1];
                final Header[] headers = wrapper.getAllHeaders();
                Assert.assertEquals(2, headers.length);
                Assert.assertEquals("this", headers[0].getValue());
                Assert.assertEquals("that", headers[1].getValue());
                wrapper.addHeader("Cookie", "monster");
                throw new IOException("Ka-boom");
            }

        });
        Mockito.when(retryHandler.retryRequest(
                Mockito.<IOException>any(),
                Mockito.eq(1),
                Mockito.<HttpContext>any())).thenReturn(Boolean.TRUE);
        try {
            retryExec.execute(route, request, context, execAware);
        } catch (final IOException ex) {
            Mockito.verify(requestExecutor, Mockito.times(2)).execute(
                    Mockito.eq(route),
                    Mockito.same(request),
                    Mockito.same(context),
                    Mockito.same(execAware));
            throw ex;
        }
    }

    @Test(expected = IOException.class)
    public void testAbortedRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenThrow(new IOException("Ka-boom"));

        Mockito.when(execAware.isAborted()).thenReturn(Boolean.TRUE);
        try {
            retryExec.execute(route, request, context, execAware);
        } catch (final IOException ex) {
            Mockito.verify(requestExecutor, Mockito.times(1)).execute(
                    Mockito.eq(route),
                    Mockito.same(request),
                    Mockito.same(context),
                    Mockito.same(execAware));
            Mockito.verify(retryHandler, Mockito.never()).retryRequest(
                    Mockito.<IOException>any(),
                    Mockito.anyInt(),
                    Mockito.<HttpContext>any());

            throw ex;
        }
    }

    @Test(expected = NonRepeatableRequestException.class)
    public void testNonRepeatableRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpPost post = new HttpPost("/test");
        post.setEntity(EntityBuilder.create()
                .setStream(new ByteArrayInputStream(new byte[]{}))
                .build());
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(post);
        final HttpClientContext context = HttpClientContext.create();

        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenAnswer(new Answer<Object>() {

            @Override
            public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final Object[] args = invocationOnMock.getArguments();
                final HttpEntityEnclosingRequest req = (HttpEntityEnclosingRequest) args[1];
                req.getEntity().writeTo(new ByteArrayOutputStream());
                throw new IOException("Ka-boom");
            }

        });
        Mockito.when(retryHandler.retryRequest(
                Mockito.<IOException>any(),
                Mockito.eq(1),
                Mockito.<HttpContext>any())).thenReturn(Boolean.TRUE);
        try {
            retryExec.execute(route, request, context, execAware);
        } catch (final IOException ex) {
            Mockito.verify(requestExecutor, Mockito.times(1)).execute(
                    Mockito.eq(route),
                    Mockito.same(request),
                    Mockito.same(context),
                    Mockito.same(execAware));

            throw ex;
        }
    }

}
