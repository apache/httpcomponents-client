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
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.RedirectException;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.NTLMScheme;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestRedirectExec {

    @Mock
    private ClientExecChain requestExecutor;
    @Mock
    private HttpRoutePlanner httpRoutePlanner;
    @Mock
    private RedirectStrategy redirectStrategy;
    @Mock
    private HttpExecutionAware execAware;

    private RedirectExec redirectExec;
    private HttpHost target;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        redirectExec = new RedirectExec(requestExecutor, httpRoutePlanner, redirectStrategy);
        target = new HttpHost("localhost", 80);
    }

    @Test
    public void testFundamentals() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        get.addHeader("header", "this");
        get.addHeader("header", "that");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response1 = Mockito.mock(CloseableHttpResponse.class);
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity1 = EntityBuilder.create()
                .setStream(instream1)
                .build();
        Mockito.when(response1.getEntity()).thenReturn(entity1);
        final CloseableHttpResponse response2 = Mockito.mock(CloseableHttpResponse.class);
        final InputStream instream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity2 = EntityBuilder.create()
                .setStream(instream2)
                .build();
        Mockito.when(response2.getEntity()).thenReturn(entity2);
        final HttpGet redirect = new HttpGet("http://localhost:80/redirect");

        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                HttpRequestWrapperMatcher.same(redirect),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response2);
        Mockito.when(redirectStrategy.isRedirected(
                Mockito.same(get),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(redirectStrategy.getRedirect(
                Mockito.same(get),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(redirect);
        Mockito.when(httpRoutePlanner.determineRoute(
                Mockito.eq(target),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        redirectExec.execute(route, request, context, execAware);

        final ArgumentCaptor<HttpRequestWrapper> reqCaptor = ArgumentCaptor.forClass(
                HttpRequestWrapper.class);
        Mockito.verify(requestExecutor, Mockito.times(2)).execute(
                Mockito.eq(route),
                reqCaptor.capture(),
                Mockito.same(context),
                Mockito.same(execAware));

        final List<HttpRequestWrapper> allValues = reqCaptor.getAllValues();
        Assert.assertNotNull(allValues);
        Assert.assertEquals(2, allValues.size());
        Assert.assertSame(request, allValues.get(0));
        final HttpRequestWrapper redirectWrapper = allValues.get(1);
        final Header[] headers = redirectWrapper.getHeaders("header");
        Assert.assertNotNull(headers);
        Assert.assertEquals(2, headers.length);
        Assert.assertEquals("this", headers[0].getValue());
        Assert.assertEquals("that", headers[1].getValue());

        Mockito.verify(response1, Mockito.times(1)).close();
        Mockito.verify(instream1, Mockito.times(1)).close();
        Mockito.verify(response2, Mockito.never()).close();
        Mockito.verify(instream2, Mockito.never()).close();
    }

    @Test(expected = RedirectException.class)
    public void testMaxRedirect() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setMaxRedirects(3)
                .build();
        context.setRequestConfig(config);

        final CloseableHttpResponse response1 = Mockito.mock(CloseableHttpResponse.class);
        final HttpGet redirect = new HttpGet("http://localhost:80/redirect");

        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(redirectStrategy.isRedirected(
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(redirectStrategy.getRedirect(
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(redirect);
        Mockito.when(httpRoutePlanner.determineRoute(
                Mockito.eq(target),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        redirectExec.execute(route, request, context, execAware);
    }

    @Test(expected = HttpException.class)
    public void testRelativeRedirect() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response1 = Mockito.mock(CloseableHttpResponse.class);
        final CloseableHttpResponse response2 = Mockito.mock(CloseableHttpResponse.class);
        final HttpGet redirect = new HttpGet("/redirect");
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                HttpRequestWrapperMatcher.same(redirect),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response2);
        Mockito.when(redirectStrategy.isRedirected(
                Mockito.same(get),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(redirectStrategy.getRedirect(
                Mockito.same(get),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(redirect);
        Mockito.when(httpRoutePlanner.determineRoute(
                Mockito.eq(target),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        redirectExec.execute(route, request, context, execAware);
    }

    @Test
    public void testCrossSiteRedirect() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();

        final AuthState targetAuthState = new AuthState();
        targetAuthState.setState(AuthProtocolState.SUCCESS);
        targetAuthState.update(new BasicScheme(), new UsernamePasswordCredentials("user", "pass"));
        final AuthState proxyAuthState = new AuthState();
        proxyAuthState.setState(AuthProtocolState.SUCCESS);
        proxyAuthState.update(new NTLMScheme(), new NTCredentials("user", "pass", null, null));
        context.setAttribute(HttpClientContext.TARGET_AUTH_STATE, targetAuthState);
        context.setAttribute(HttpClientContext.PROXY_AUTH_STATE, proxyAuthState);

        final CloseableHttpResponse response1 = Mockito.mock(CloseableHttpResponse.class);
        final CloseableHttpResponse response2 = Mockito.mock(CloseableHttpResponse.class);
        final HttpGet redirect = new HttpGet("http://otherhost/redirect");
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                HttpRequestWrapperMatcher.same(redirect),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response2);
        Mockito.when(redirectStrategy.isRedirected(
                Mockito.same(get),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(redirectStrategy.getRedirect(
                Mockito.same(get),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(redirect);
        Mockito.when(httpRoutePlanner.determineRoute(
                Mockito.eq(target),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any())).thenReturn(new HttpRoute(new HttpHost("otherhost", 80)));

        redirectExec.execute(route, request, context, execAware);

        Assert.assertNotNull(context.getTargetAuthState());
        Assert.assertEquals(AuthProtocolState.UNCHALLENGED, context.getTargetAuthState().getState());
        Assert.assertEquals(null, context.getTargetAuthState().getAuthScheme());
        Assert.assertNotNull(context.getProxyAuthState());
        Assert.assertEquals(AuthProtocolState.UNCHALLENGED, context.getProxyAuthState().getState());
        Assert.assertEquals(null, context.getProxyAuthState().getAuthScheme());
    }

    @Test(expected = RuntimeException.class)
    public void testRedirectRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response1 = Mockito.mock(CloseableHttpResponse.class);
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(redirectStrategy.isRedirected(
                Mockito.same(request),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.doThrow(new RuntimeException("Oppsie")).when(redirectStrategy.getRedirect(
                Mockito.same(request),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any()));

        try {
            redirectExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response1).close();
            throw ex;
        }
    }

    @Test(expected = ProtocolException.class)
    public void testRedirectProtocolException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(get);
        final HttpClientContext context = HttpClientContext.create();

        final CloseableHttpResponse response1 = Mockito.mock(CloseableHttpResponse.class);
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity1 = EntityBuilder.create()
                .setStream(instream1)
                .build();
        Mockito.when(response1.getEntity()).thenReturn(entity1);
        Mockito.when(requestExecutor.execute(
                Mockito.eq(route),
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(redirectStrategy.isRedirected(
                Mockito.same(get),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.doThrow(new ProtocolException("Oppsie")).when(redirectStrategy).getRedirect(
                Mockito.same(get),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any());

        try {
            redirectExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(instream1).close();
            Mockito.verify(response1).close();
            throw ex;
        }
    }

    static class HttpRequestWrapperMatcher extends ArgumentMatcher<HttpRequestWrapper> {

        private final HttpRequest original;

        HttpRequestWrapperMatcher(final HttpRequest original) {
            super();
            this.original = original;
        }
        @Override
        public boolean matches(final Object obj) {
            final HttpRequestWrapper wrapper = (HttpRequestWrapper) obj;
            return original == wrapper.getOriginal();
        }

        static HttpRequestWrapper same(final HttpRequest original) {
            return Matchers.argThat(new HttpRequestWrapperMatcher(original));
        }

    }

}
