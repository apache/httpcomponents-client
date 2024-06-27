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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.CircularRedirectException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RedirectException;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.io.support.ClassicResponseBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class TestRedirectExec {

    @Mock
    private HttpRoutePlanner httpRoutePlanner;
    @Mock
    private ExecChain chain;
    @Mock
    private ExecRuntime endpoint;

    private RedirectStrategy redirectStrategy;
    private RedirectExec redirectExec;
    private HttpHost target;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        target = new HttpHost("localhost", 80);
        redirectStrategy = Mockito.spy(new DefaultRedirectStrategy());
        redirectExec = new RedirectExec(httpRoutePlanner, redirectStrategy);
    }

    @Test
    void testFundamentals() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response1 = Mockito.spy(new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY));
        final URI redirect = new URI("http://localhost:80/redirect");
        response1.setHeader(HttpHeaders.LOCATION, redirect.toASCIIString());
        final InputStream inStream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity1 = EntityBuilder.create()
                .setStream(inStream1)
                .build();
        response1.setEntity(entity1);
        final ClassicHttpResponse response2 = Mockito.spy(new BasicClassicHttpResponse(HttpStatus.SC_OK));
        final InputStream inStream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity2 = EntityBuilder.create()
                .setStream(inStream2)
                .build();
        response2.setEntity(entity2);

        Mockito.when(chain.proceed(
                ArgumentMatchers.same(request),
                ArgumentMatchers.any())).thenReturn(response1);
        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(redirect),
                ArgumentMatchers.any())).thenReturn(response2);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        redirectExec.execute(request, scope, chain);

        final ArgumentCaptor<ClassicHttpRequest> reqCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(chain, Mockito.times(2)).proceed(reqCaptor.capture(), ArgumentMatchers.same(scope));

        final List<ClassicHttpRequest> allValues = reqCaptor.getAllValues();
        Assertions.assertNotNull(allValues);
        Assertions.assertEquals(2, allValues.size());
        Assertions.assertSame(request, allValues.get(0));

        Mockito.verify(response1, Mockito.times(1)).close();
        Mockito.verify(inStream1, Mockito.times(2)).close();
        Mockito.verify(response2, Mockito.never()).close();
        Mockito.verify(inStream2, Mockito.never()).close();
    }

    @Test
    void testMaxRedirect() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();
        final RequestConfig config = RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setMaxRedirects(3)
                .build();
        context.setRequestConfig(config);

        final ClassicHttpResponse response1 = Mockito.spy(new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY));
        final URI redirect = new URI("http://localhost:80/redirect");
        response1.setHeader(HttpHeaders.LOCATION, redirect.toASCIIString());

        Mockito.when(chain.proceed(ArgumentMatchers.any(), ArgumentMatchers.any())).thenReturn(response1);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        Assertions.assertThrows(RedirectException.class, () ->
                redirectExec.execute(request, scope, chain));
    }

    @Test
    void testRelativeRedirect() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response1 = Mockito.spy(new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY));
        final URI redirect = new URI("/redirect");
        response1.setHeader(HttpHeaders.LOCATION, redirect.toASCIIString());
        Mockito.when(chain.proceed(
                ArgumentMatchers.same(request),
                ArgumentMatchers.any())).thenReturn(response1);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        Assertions.assertThrows(HttpException.class, () ->
                redirectExec.execute(request, scope, chain));
    }

    @Test
    void testCrossSiteRedirect() throws Exception {

        final HttpHost proxy = new HttpHost("proxy");
        final HttpRoute route = new HttpRoute(target, proxy);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final AuthExchange targetAuthExchange = new AuthExchange();
        targetAuthExchange.setState(AuthExchange.State.SUCCESS);
        targetAuthExchange.select(new BasicScheme());
        final AuthExchange proxyAuthExchange = new AuthExchange();
        proxyAuthExchange.setState(AuthExchange.State.SUCCESS);
        proxyAuthExchange.select(new BasicScheme() {

            @Override
            public boolean isConnectionBased() {
                return true;
            }

        });
        context.setAuthExchange(target, targetAuthExchange);
        context.setAuthExchange(proxy, proxyAuthExchange);

        final ClassicHttpResponse response1 = Mockito.spy(new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY));
        final URI redirect = new URI("http://otherhost:8888/redirect");
        response1.setHeader(HttpHeaders.LOCATION, redirect.toASCIIString());
        final ClassicHttpResponse response2 = Mockito.spy(new BasicClassicHttpResponse(HttpStatus.SC_OK));
        final HttpHost otherHost = new HttpHost("otherhost", 8888);
        Mockito.when(chain.proceed(
                ArgumentMatchers.same(request),
                ArgumentMatchers.any())).thenReturn(response1);
        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(redirect),
                ArgumentMatchers.any())).thenReturn(response2);
        Mockito.when(httpRoutePlanner.determineRoute(
                ArgumentMatchers.eq(otherHost),
                ArgumentMatchers.<HttpClientContext>any())).thenReturn(new HttpRoute(otherHost));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        redirectExec.execute(request, scope, chain);

        final AuthExchange authExchange1 = context.getAuthExchange(target);
        Assertions.assertNotNull(authExchange1);
        Assertions.assertEquals(AuthExchange.State.UNCHALLENGED, authExchange1.getState());
        Assertions.assertNull(authExchange1.getAuthScheme());
        final AuthExchange authExchange2 = context.getAuthExchange(proxy);
        Assertions.assertNotNull(authExchange2);
        Assertions.assertEquals(AuthExchange.State.UNCHALLENGED, authExchange2.getState());
        Assertions.assertNull(authExchange2.getAuthScheme());
    }

    @Test
    void testAllowCircularRedirects() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom()
                .setCircularRedirectsAllowed(true)
                .build());

        final URI uri = URI.create("http://localhost/");
        final HttpGet request = new HttpGet(uri);

        final URI uri1 = URI.create("http://localhost/stuff1");
        final URI uri2 = URI.create("http://localhost/stuff2");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
        response1.addHeader("Location", uri1.toASCIIString());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
        response2.addHeader("Location", uri2.toASCIIString());
        final ClassicHttpResponse response3 = new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
        response3.addHeader("Location", uri1.toASCIIString());
        final ClassicHttpResponse response4 = new BasicClassicHttpResponse(HttpStatus.SC_OK);

        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(uri),
                ArgumentMatchers.any())).thenReturn(response1);
        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(uri1),
                ArgumentMatchers.any())).thenReturn(response2, response4);
        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(uri2),
                ArgumentMatchers.any())).thenReturn(response3);
        Mockito.when(httpRoutePlanner.determineRoute(
                ArgumentMatchers.eq(new HttpHost("localhost")),
                ArgumentMatchers.<HttpClientContext>any())).thenReturn(route);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        redirectExec.execute(request, scope, chain);

        final RedirectLocations uris = context.getRedirectLocations();
        Assertions.assertNotNull(uris);
        Assertions.assertEquals(Arrays.asList(uri1, uri2, uri1), uris.getAll());
    }

    @Test
    void testGetLocationUriDisallowCircularRedirects() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = HttpClientContext.create();
        context.setRequestConfig(RequestConfig.custom()
                .setCircularRedirectsAllowed(false)
                .build());

        final URI uri = URI.create("http://localhost/");
        final HttpGet request = new HttpGet(uri);

        final URI uri1 = URI.create("http://localhost/stuff1");
        final URI uri2 = URI.create("http://localhost/stuff2");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
        response1.addHeader("Location", uri1.toASCIIString());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
        response2.addHeader("Location", uri2.toASCIIString());
        final ClassicHttpResponse response3 = new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY);
        response3.addHeader("Location", uri1.toASCIIString());
        Mockito.when(httpRoutePlanner.determineRoute(
                ArgumentMatchers.eq(new HttpHost("localhost")),
                ArgumentMatchers.<HttpClientContext>any())).thenReturn(route);

        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(uri),
                ArgumentMatchers.any())).thenReturn(response1);
        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(uri1),
                ArgumentMatchers.any())).thenReturn(response2);
        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(uri2),
                ArgumentMatchers.any())).thenReturn(response3);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        Assertions.assertThrows(CircularRedirectException.class, () ->
                redirectExec.execute(request, scope, chain));
    }

    @Test
    void testRedirectRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response1 = Mockito.spy(new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY));
        final URI redirect = new URI("http://localhost:80/redirect");
        response1.setHeader(HttpHeaders.LOCATION, redirect.toASCIIString());
        Mockito.when(chain.proceed(
                ArgumentMatchers.same(request),
                ArgumentMatchers.any())).thenReturn(response1);
        Mockito.doThrow(new RuntimeException("Oppsie")).when(redirectStrategy).getLocationURI(
                ArgumentMatchers.<ClassicHttpRequest>any(),
                ArgumentMatchers.<ClassicHttpResponse>any(),
                ArgumentMatchers.<HttpClientContext>any());

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        Assertions.assertThrows(RuntimeException.class, () ->
                redirectExec.execute(request, scope, chain));
        Mockito.verify(response1).close();
    }

    @Test
    void testRedirectProtocolException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpGet request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        final ClassicHttpResponse response1 = Mockito.spy(new BasicClassicHttpResponse(HttpStatus.SC_MOVED_TEMPORARILY));
        final URI redirect = new URI("http://localhost:80/redirect");
        response1.setHeader(HttpHeaders.LOCATION, redirect.toASCIIString());
        final InputStream inStream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        final HttpEntity entity1 = EntityBuilder.create()
                .setStream(inStream1)
                .build();
        response1.setEntity(entity1);
        Mockito.when(chain.proceed(
                ArgumentMatchers.same(request),
                ArgumentMatchers.any())).thenReturn(response1);
        Mockito.doThrow(new ProtocolException("Oppsie")).when(redirectStrategy).getLocationURI(
                ArgumentMatchers.<ClassicHttpRequest>any(),
                ArgumentMatchers.<ClassicHttpResponse>any(),
                ArgumentMatchers.<HttpClientContext>any());

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        Assertions.assertThrows(ProtocolException.class, () ->
                redirectExec.execute(request, scope, chain));
        Mockito.verify(inStream1, Mockito.times(2)).close();
        Mockito.verify(response1).close();
    }

    @Test
    void testPutSeeOtherRedirect() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final URI targetUri = new URI("http://localhost:80/stuff");
        final ClassicHttpRequest request = ClassicRequestBuilder.put()
                .setUri(targetUri)
                .setEntity("stuff")
                .build();
        final HttpClientContext context = HttpClientContext.create();

        final URI redirect1 = new URI("http://localhost:80/see-something-else");
        final ClassicHttpResponse response1 = ClassicResponseBuilder.create(HttpStatus.SC_SEE_OTHER)
                .addHeader(HttpHeaders.LOCATION, redirect1.toASCIIString())
                .build();
        final URI redirect2 = new URI("http://localhost:80/other-stuff");
        final ClassicHttpResponse response2 = ClassicResponseBuilder.create(HttpStatus.SC_MOVED_PERMANENTLY)
                .addHeader(HttpHeaders.LOCATION, redirect2.toASCIIString())
                .build();
        final ClassicHttpResponse response3 = ClassicResponseBuilder.create(HttpStatus.SC_OK)
                .build();

        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(targetUri),
                ArgumentMatchers.any())).thenReturn(response1);
        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(redirect1),
                ArgumentMatchers.any())).thenReturn(response2);
        Mockito.when(chain.proceed(
                HttpRequestMatcher.matchesRequestUri(redirect2),
                ArgumentMatchers.any())).thenReturn(response3);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = redirectExec.execute(request, scope, chain);
        Assertions.assertEquals(200, finalResponse.getCode());

        final ArgumentCaptor<ClassicHttpRequest> reqCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(chain, Mockito.times(3)).proceed(reqCaptor.capture(), ArgumentMatchers.same(scope));

        final List<ClassicHttpRequest> allValues = reqCaptor.getAllValues();
        Assertions.assertNotNull(allValues);
        Assertions.assertEquals(3, allValues.size());
        final ClassicHttpRequest request1 = allValues.get(0);
        final ClassicHttpRequest request2 = allValues.get(1);
        final ClassicHttpRequest request3 = allValues.get(2);
        Assertions.assertSame(request, request1);
        Assertions.assertEquals(request1.getMethod(), "PUT");
        Assertions.assertEquals(request2.getMethod(), "GET");
        Assertions.assertEquals(request3.getMethod(), "GET");
    }

    private static class HttpRequestMatcher implements ArgumentMatcher<ClassicHttpRequest> {

        private final URI expectedRequestUri;

        HttpRequestMatcher(final URI requestUri) {
            super();
            this.expectedRequestUri = requestUri;
        }

        @Override
        public boolean matches(final ClassicHttpRequest argument) {
            if (argument == null) {
                return false;
            }
            try {
                final URI requestUri = argument.getUri();
                return this.expectedRequestUri.equals(requestUri);
            } catch (final URISyntaxException ex) {
                return false;
            }
        }

        static ClassicHttpRequest matchesRequestUri(final URI requestUri) {
            return ArgumentMatchers.argThat(new HttpRequestMatcher(requestUri));
        }

    }

}
