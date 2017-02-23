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

import java.io.IOException;
import java.net.URI;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.sync.methods.HttpExecutionAware;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@SuppressWarnings({"static-access"}) // test code
public class TestProtocolExec {

    @Mock
    private ClientExecChain requestExecutor;
    @Mock
    private HttpProcessor httpProcessor;
    @Mock
    private HttpExecutionAware execAware;

    private ProtocolExec protocolExec;
    private HttpHost target;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        protocolExec = new ProtocolExec(requestExecutor, httpProcessor);
        target = new HttpHost("foo", 80);
    }

    @Test
    public void testFundamentals() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(new HttpGet("/test"), route);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(requestExecutor.execute(
                Mockito.<RoutedHttpRequest>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);

        protocolExec.execute(request, context, execAware);

        Mockito.verify(httpProcessor).process(request, null, context);
        Mockito.verify(requestExecutor).execute(request, context, execAware);
        Mockito.verify(httpProcessor).process(response, null, context);

        Assert.assertEquals(route, context.getHttpRoute());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());
    }

    @Test
    public void testUserInfoInRequestURI() throws Exception {
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 8080));
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(
                new HttpGet("http://somefella:secret@bar/test"), route);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(new BasicCredentialsProvider());

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(requestExecutor.execute(
                Mockito.<RoutedHttpRequest>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);

        protocolExec.execute(request, context, execAware);
        Assert.assertEquals(new URI("http://bar/test"), request.getUri());
        final CredentialsProvider credentialsProvider = context.getCredentialsProvider();
        final Credentials creds = credentialsProvider.getCredentials(new AuthScope("bar", -1, null), null);
        Assert.assertNotNull(creds);
        Assert.assertEquals("somefella", creds.getUserPrincipal().getName());
    }

    @Test(expected = HttpException.class)
    public void testPostProcessHttpException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(new HttpGet("/test"), route);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);

        Mockito.when(requestExecutor.execute(
                Mockito.<RoutedHttpRequest>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);
        Mockito.doThrow(new HttpException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.isNull(EntityDetails.class), Mockito.<HttpContext>any());
        try {
            protocolExec.execute(request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
    }

    @Test(expected = IOException.class)
    public void testPostProcessIOException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(new HttpGet("/test"), route);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(requestExecutor.execute(
                Mockito.<RoutedHttpRequest>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);
        Mockito.doThrow(new IOException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.isNull(EntityDetails.class), Mockito.<HttpContext>any());
        try {
            protocolExec.execute(request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
    }

    @Test(expected = RuntimeException.class)
    public void testPostProcessRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(new HttpGet("/test"), route);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(requestExecutor.execute(
                Mockito.<RoutedHttpRequest>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);
        Mockito.doThrow(new RuntimeException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.isNull(EntityDetails.class), Mockito.<HttpContext>any());
        try {
            protocolExec.execute(request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
    }

}
