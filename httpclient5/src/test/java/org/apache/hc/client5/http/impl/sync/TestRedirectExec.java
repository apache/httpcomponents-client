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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.NTLMScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectException;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.sync.methods.HttpExecutionAware;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(get, route);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response1 = Mockito.mock(ClassicHttpResponse.class);
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity1 = EntityBuilder.create()
                .setStream(instream1)
                .build();
        Mockito.when(response1.getEntity()).thenReturn(entity1);
        final ClassicHttpResponse response2 = Mockito.mock(ClassicHttpResponse.class);
        final InputStream instream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity2 = EntityBuilder.create()
                .setStream(instream2)
                .build();
        Mockito.when(response2.getEntity()).thenReturn(entity2);
        final HttpGet redirect = new HttpGet("http://localhost:80/redirect");

        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(requestExecutor.execute(
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
                Mockito.<HttpClientContext>any())).thenReturn(route);

        redirectExec.execute(request, context, execAware);

        final ArgumentCaptor<RoutedHttpRequest> reqCaptor = ArgumentCaptor.forClass(
                RoutedHttpRequest.class);
        Mockito.verify(requestExecutor, Mockito.times(2)).execute(
                reqCaptor.capture(),
                Mockito.same(context),
                Mockito.same(execAware));

        final List<RoutedHttpRequest> allValues = reqCaptor.getAllValues();
        Assert.assertNotNull(allValues);
        Assert.assertEquals(2, allValues.size());
        Assert.assertSame(request, allValues.get(0));

        Mockito.verify(response1, Mockito.times(1)).close();
        Mockito.verify(instream1, Mockito.times(1)).close();
        Mockito.verify(response2, Mockito.never()).close();
        Mockito.verify(instream2, Mockito.never()).close();
    }

    @Test(expected = RedirectException.class)
    public void testMaxRedirect() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(get, route);
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setMaxRedirects(3)
                .build();
        context.setRequestConfig(config);

        final ClassicHttpResponse response1 = Mockito.mock(ClassicHttpResponse.class);
        final HttpGet redirect = new HttpGet("http://localhost:80/redirect");

        Mockito.when(requestExecutor.execute(
                Mockito.<RoutedHttpRequest>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(redirectStrategy.isRedirected(
                Mockito.<RoutedHttpRequest>any(),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(redirectStrategy.getRedirect(
                Mockito.<RoutedHttpRequest>any(),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(redirect);
        Mockito.when(httpRoutePlanner.determineRoute(
                Mockito.eq(target),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        redirectExec.execute(request, context, execAware);
    }

    @Test(expected = HttpException.class)
    public void testRelativeRedirect() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(get, route);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response1 = Mockito.mock(ClassicHttpResponse.class);
        final ClassicHttpResponse response2 = Mockito.mock(ClassicHttpResponse.class);
        final HttpGet redirect = new HttpGet("/redirect");
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(requestExecutor.execute(
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
                Mockito.<HttpClientContext>any())).thenReturn(route);

        redirectExec.execute(request, context, execAware);
    }

    @Test
    public void testCrossSiteRedirect() throws Exception {

        final HttpHost proxy = new HttpHost("proxy");
        final HttpRoute route = new HttpRoute(target, proxy);
        final HttpGet get = new HttpGet("/test");
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(get, route);
        final HttpClientContext context = HttpClientContext.create();

        final AuthExchange targetAuthExchange = new AuthExchange();
        targetAuthExchange.setState(AuthExchange.State.SUCCESS);
        targetAuthExchange.select(new BasicScheme());
        final AuthExchange proxyAuthExchange = new AuthExchange();
        proxyAuthExchange.setState(AuthExchange.State.SUCCESS);
        proxyAuthExchange.select(new NTLMScheme());
        context.setAuthExchange(target, targetAuthExchange);
        context.setAuthExchange(proxy, proxyAuthExchange);

        final ClassicHttpResponse response1 = Mockito.mock(ClassicHttpResponse.class);
        final ClassicHttpResponse response2 = Mockito.mock(ClassicHttpResponse.class);
        final HttpHost otherHost = new HttpHost("otherhost", 8888);
        final HttpGet redirect = new HttpGet("http://otherhost:8888/redirect");
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any(),
                Mockito.<HttpExecutionAware>any())).thenReturn(response1);
        Mockito.when(requestExecutor.execute(
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
                Mockito.<HttpClientContext>any())).thenReturn(new HttpRoute(target));
        Mockito.when(httpRoutePlanner.determineRoute(
                Mockito.eq(otherHost),
                Mockito.<HttpClientContext>any())).thenReturn(new HttpRoute(otherHost));

        redirectExec.execute(request, context, execAware);

        final AuthExchange authExchange1 = context.getAuthExchange(target);
        Assert.assertNotNull(authExchange1);
        Assert.assertEquals(AuthExchange.State.UNCHALLENGED, authExchange1.getState());
        Assert.assertEquals(null, authExchange1.getAuthScheme());
        final AuthExchange authExchange2 = context.getAuthExchange(proxy);
        Assert.assertNotNull(authExchange2);
        Assert.assertEquals(AuthExchange.State.UNCHALLENGED, authExchange2.getState());
        Assert.assertEquals(null, authExchange2.getAuthScheme());
    }

    @Test(expected = RuntimeException.class)
    public void testRedirectRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(get, route);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response1 = Mockito.mock(ClassicHttpResponse.class);
        Mockito.when(requestExecutor.execute(
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
            redirectExec.execute(request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(response1).close();
            throw ex;
        }
    }

    @Test(expected = ProtocolException.class)
    public void testRedirectProtocolException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet get = new HttpGet("/test");
        final RoutedHttpRequest request = RoutedHttpRequest.adapt(get, route);
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response1 = Mockito.mock(ClassicHttpResponse.class);
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity1 = EntityBuilder.create()
                .setStream(instream1)
                .build();
        Mockito.when(response1.getEntity()).thenReturn(entity1);
        Mockito.when(requestExecutor.execute(
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
            redirectExec.execute(request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(instream1).close();
            Mockito.verify(response1).close();
            throw ex;
        }
    }

    static class HttpRequestWrapperMatcher extends ArgumentMatcher<RoutedHttpRequest> {

        private final HttpRequest original;

        HttpRequestWrapperMatcher(final HttpRequest original) {
            super();
            this.original = original;
        }
        @Override
        public boolean matches(final Object obj) {
            final RoutedHttpRequest wrapper = (RoutedHttpRequest) obj;
            return original == wrapper.getOriginal();
        }

        static RoutedHttpRequest same(final HttpRequest original) {
            return Matchers.argThat(new HttpRequestWrapperMatcher(original));
        }

    }

}
