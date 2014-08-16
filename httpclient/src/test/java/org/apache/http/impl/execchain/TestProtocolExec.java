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

import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
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
    private HttpHost proxy;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        protocolExec = new ProtocolExec(requestExecutor, httpProcessor);
        target = new HttpHost("foo", 80);
        proxy = new HttpHost("bar", 8888);
    }

    @Test
    public void testFundamentals() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("/test"));
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);

        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);

        protocolExec.execute(route, request, context, execAware);

        Mockito.verify(httpProcessor).process(request, context);
        Mockito.verify(requestExecutor).execute(route, request, context, execAware);
        Mockito.verify(httpProcessor).process(response, context);

        Assert.assertEquals(new HttpHost("foo", 80), context.getTargetHost());
        Assert.assertEquals(target, context.getTargetHost());
        Assert.assertEquals(route, context.getHttpRoute());
        Assert.assertSame(request, context.getRequest());
        Assert.assertSame(response, context.getResponse());
    }

    @Test
    public void testRewriteAbsoluteRequestURI() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new HttpGet("http://foo/test"));
        protocolExec.rewriteRequestURI(request, route);
        Assert.assertEquals(new URI("/test"), request.getURI());
    }

    @Test
    public void testRewriteEmptyRequestURI() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new HttpGet(""));
        protocolExec.rewriteRequestURI(request, route);
        Assert.assertEquals(new URI("/"), request.getURI());
    }

    @Test
    public void testRewriteAbsoluteRequestURIViaPRoxy() throws Exception {
        final HttpRoute route = new HttpRoute(target, proxy);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new HttpGet("http://foo/test"));
        protocolExec.rewriteRequestURI(request, route);
        Assert.assertEquals(new URI("http://foo/test"), request.getURI());
    }

    @Test
    public void testRewriteRelativeRequestURIViaPRoxy() throws Exception {
        final HttpRoute route = new HttpRoute(target, proxy);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new HttpGet("/test"));
        protocolExec.rewriteRequestURI(request, route);
        Assert.assertEquals(new URI("http://foo:80/test"), request.getURI());
    }

    @Test
    public void testHostHeaderUriRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new HttpGet("http://bar/test"));
        final HttpClientContext context = HttpClientContext.create();
        protocolExec.execute(route, request, context, execAware);
        // ProtocolExect should have extracted the host from request URI
        Assert.assertEquals(new HttpHost("bar", -1, "http"), context.getTargetHost());
    }

    @Test
    public void testHostHeaderWhenNonUriRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "http://bar/test"));
        final HttpClientContext context = HttpClientContext.create();
        protocolExec.execute(route, request, context, execAware);
        // ProtocolExect should have extracted the host from request URI
        Assert.assertEquals(new HttpHost("bar", -1, "http"), context.getTargetHost());
    }

    @Test
    public void testHostHeaderWhenNonUriRequestAndInvalidUri() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new BasicHttpRequest("GET", "http://bar/test|"));
        final HttpClientContext context = HttpClientContext.create();
        protocolExec.execute(route, request, context, execAware);
        // ProtocolExect should have fall back to physical host as request URI
        // is not parseable
        Assert.assertEquals(new HttpHost("foo", 80, "http"), context.getTargetHost());
    }

    @Test
    public void testHostHeaderImplicitHost() throws Exception {
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 8080));
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new HttpGet("/test"));
        final HttpClientContext context = HttpClientContext.create();
        protocolExec.execute(route, request, context, execAware);
        Assert.assertEquals(new HttpHost("somehost", 8080), context.getTargetHost());
    }

    @Test
    public void testUserInfoInRequestURI() throws Exception {
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 8080));
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(
                new HttpGet("http://somefella:secret@bar/test"));
        final HttpClientContext context = HttpClientContext.create();
        protocolExec.execute(route, request, context, execAware);
        Assert.assertEquals(new URI("/test"), request.getURI());
        Assert.assertEquals(new HttpHost("bar", -1), context.getTargetHost());
        final CredentialsProvider credentialsProvider = context.getCredentialsProvider();
        Assert.assertNotNull(credentialsProvider);
        final Credentials creds = credentialsProvider.getCredentials(new AuthScope("bar", -1, null));
        Assert.assertNotNull(creds);
        Assert.assertEquals("somefella", creds.getUserPrincipal().getName());
    }

    @Test(expected = HttpException.class)
    public void testPostProcessHttpException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("/test"));
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);

        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);
        Mockito.doThrow(new HttpException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.<HttpContext>any());
        try {
            protocolExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
    }

    @Test(expected = IOException.class)
    public void testPostProcessIOException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("/test"));
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);
        Mockito.doThrow(new IOException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.<HttpContext>any());
        try {
            protocolExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
    }

    @Test(expected = RuntimeException.class)
    public void testPostProcessRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("/test"));
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response = Mockito.mock(CloseableHttpResponse.class);
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response);
        Mockito.doThrow(new RuntimeException("Ooopsie")).when(httpProcessor).process(
                Mockito.same(response), Mockito.<HttpContext>any());
        try {
            protocolExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response).close();
            throw ex;
        }
    }

}
