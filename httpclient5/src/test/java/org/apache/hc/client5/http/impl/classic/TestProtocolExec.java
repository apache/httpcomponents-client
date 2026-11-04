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
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.hc.client5.http.AuthenticationStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.RequestSupport;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.function.Factory;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.support.ClassicResponseBuilder;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@SuppressWarnings({"static-access"}) // test code
@ExtendWith(MockitoExtension.class)
class TestProtocolExec {

    @Mock
    private AuthenticationStrategy targetAuthStrategy;
    @Mock
    private AuthenticationStrategy proxyAuthStrategy;
    @Mock
    private AuthCache authCache;
    @Mock
    private ExecChain chain;
    @Mock
    private ExecRuntime execRuntime;

    private AtomicBoolean pathPrefixExtracted;
    private Factory<HttpRequest, Supplier<String>> prefixExtractorFactory;
    private ProtocolExec protocolExec;
    private HttpHost target;

    @BeforeEach
    void setup() {
        pathPrefixExtracted = new AtomicBoolean();
        prefixExtractorFactory = request -> () -> {
            pathPrefixExtracted.set(true);
            return RequestSupport.extractPathPrefix(request.getPath());
        };
        protocolExec = new ProtocolExec(targetAuthStrategy, proxyAuthStrategy, null, prefixExtractorFactory, false);
        target = new HttpHost("foo", 80);
    }

    @Test
    void testNoAuthChallengeNoAuthCache() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/aaaa/bbbb");
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(null);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(new BasicClassicHttpResponse(200));

        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);

        Assertions.assertNotNull(finalResponse);

        final AuthExchange authExchange = context.getAuthExchange(target);
        Assertions.assertNotNull(authExchange);
        Assertions.assertEquals(AuthExchange.State.UNCHALLENGED, authExchange.getState());
        Assertions.assertNull(authExchange.getPathPrefix());

        final AuthCache authCache = context.getAuthCache();
        Assertions.assertNull(authCache);
        Assertions.assertFalse(pathPrefixExtracted.get());
    }

    @ParameterizedTest(name = "{index}: auth caching enabled: {0}")
    @CsvSource({"true", "false"})
    void testNoAuthChallenge(final boolean authCacheEnabled) throws Exception {
        if (!authCacheEnabled) {
            protocolExec = new ProtocolExec(targetAuthStrategy, proxyAuthStrategy, null, prefixExtractorFactory, true);
        }
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/aaaa/bbbb");
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(authCache);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(new BasicClassicHttpResponse(200));

        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);

        Assertions.assertNotNull(finalResponse);

        final AuthExchange authExchange = context.getAuthExchange(target);
        Assertions.assertNotNull(authExchange);
        Assertions.assertEquals(AuthExchange.State.UNCHALLENGED, authExchange.getState());
        Assertions.assertNull(authExchange.getPathPrefix());

        if (authCacheEnabled) {
            Mockito.verify(authCache).get(target, "/aaaa/");
            Mockito.verify(authCache).get(target, null);
            Mockito.verifyNoMoreInteractions(authCache);
        } else {
            Mockito.verifyNoInteractions(authCache);
            Assertions.assertFalse(pathPrefixExtracted.get());
        }
    }

    @ParameterizedTest(name = "{index}: auth caching enabled: {0}")
    @CsvSource({"true", "false"})
    void testAuthChallengeFailure(final boolean authCacheEnabled) throws Exception {
        if (!authCacheEnabled) {
            protocolExec = new ProtocolExec(targetAuthStrategy, proxyAuthStrategy, null, prefixExtractorFactory,true);
        }
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/aaaa/bbbb");
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(authCache);
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, new UsernamePasswordCredentials("test", "banana".toCharArray()))
                .build());

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(ClassicResponseBuilder.create(401)
                .addHeader(HttpHeaders.WWW_AUTHENTICATE, "BASIC realm=test")
                .build());

        final BasicScheme basicScheme = new BasicScheme();
        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.singletonList(basicScheme));

        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);

        Assertions.assertNotNull(finalResponse);

        final AuthExchange authExchange = context.getAuthExchange(target);
        Assertions.assertNotNull(authExchange);
        Assertions.assertEquals(AuthExchange.State.FAILURE, authExchange.getState());
        Assertions.assertEquals("/aaaa/", authExchange.getPathPrefix());

        if (authCacheEnabled) {
            Mockito.verify(authCache).get(target, "/aaaa/");
            Mockito.verify(authCache).get(target, null);
            Mockito.verify(authCache, Mockito.atLeastOnce()).remove(target, "/aaaa/");
            Mockito.verifyNoMoreInteractions(authCache);
        } else {
            Mockito.verifyNoInteractions(authCache);
            Assertions.assertTrue(pathPrefixExtracted.get());
        }
    }

    @ParameterizedTest(name = "{index}: auth caching enabled: {0}")
    @CsvSource({"true", "false"})
    void testAuthChallengeSuccess(final boolean authCacheEnabled) throws Exception {
        if (!authCacheEnabled) {
            protocolExec = new ProtocolExec(targetAuthStrategy, proxyAuthStrategy, null, prefixExtractorFactory,true);
        }
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/aaaa/bbbb");
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(authCache);
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(target, new UsernamePasswordCredentials("test", "test".toCharArray()))
                .build());

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(
                ClassicResponseBuilder.create(401)
                        .addHeader(HttpHeaders.WWW_AUTHENTICATE, "BASIC realm=test")
                        .build(),
                ClassicResponseBuilder.create(200)
                        .build());

        final BasicScheme basicScheme = new BasicScheme();
        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.singletonList(basicScheme));

        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);

        Assertions.assertNotNull(finalResponse);

        if (authCacheEnabled) {
            Mockito.verify(authCache).get(target, "/aaaa/");
            Mockito.verify(authCache).get(target, null);
            Mockito.verify(authCache, Mockito.atLeastOnce()).remove(target, "/aaaa/");
            Mockito.verify(authCache).put(target, "/aaaa/", basicScheme);
            Mockito.verifyNoMoreInteractions(authCache);
        } else {
            Mockito.verifyNoInteractions(authCache);
            Assertions.assertTrue(pathPrefixExtracted.get());
        }

        final AuthExchange authExchange = context.getAuthExchange(target);
        Assertions.assertNotNull(authExchange);
        Assertions.assertEquals(AuthExchange.State.SUCCESS, authExchange.getState());
        Assertions.assertEquals("/aaaa/", authExchange.getPathPrefix());
    }

    @Test
    void testUserInfoInRequestURI() {
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 8080));
        final ClassicHttpRequest request = new HttpGet("http://somefella:secret@bar/test");
        final HttpClientContext context = HttpClientContext.create();

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(ProtocolException.class, () -> protocolExec.execute(request, scope, chain));
    }

    @Test
    void testPostProcessHttpException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        Mockito.doThrow(new HttpException("Ooopsie")).when(chain).proceed(Mockito.any(), Mockito.any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(HttpException.class, () ->
                protocolExec.execute(request, scope, chain));
        Mockito.verify(execRuntime).discardEndpoint();
    }

    @Test
    void testPostProcessIOException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        Mockito.doThrow(new IOException("Ooopsie")).when(chain).proceed(Mockito.any(), Mockito.any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(IOException.class, () ->
                protocolExec.execute(request, scope, chain));
        Mockito.verify(execRuntime).discardEndpoint();
    }

    @Test
    void testPostProcessRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("/test");
        final HttpClientContext context = HttpClientContext.create();

        Mockito.doThrow(new RuntimeException("Ooopsie")).when(chain).proceed(Mockito.any(), Mockito.any());
        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        Assertions.assertThrows(RuntimeException.class, () ->
                protocolExec.execute(request, scope, chain));
        Mockito.verify(execRuntime).discardEndpoint();
    }

    @Test
    void testExecRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = HttpClientContext.create();
        final ClassicHttpRequest request = new HttpGet("http://foo/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final InputStream inStream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(inStream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");
        final InputStream inStream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        response2.setEntity(EntityBuilder.create()
                .setStream(inStream2)
                .build());

        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(response1, response2);
        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.singletonList(new BasicScheme()));
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Mockito.verify(chain, Mockito.times(2)).proceed(request, scope);
        Mockito.verify(inStream1).close();
        Mockito.verify(inStream2, Mockito.never()).close();

        Assertions.assertNotNull(finalResponse);
        Assertions.assertEquals(200, finalResponse.getCode());
    }

    @Test
    void testExecEntityEnclosingRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("http://foo/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final InputStream inStream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(inStream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");
        final InputStream inStream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        response2.setEntity(EntityBuilder.create()
                .setStream(inStream2)
                .build());

        final HttpClientContext context = HttpClientContext.create();

        final AuthExchange authExchange = new AuthExchange();
        authExchange.setState(AuthExchange.State.SUCCESS);
        authExchange.select(new BasicScheme() {

            @Override
            public boolean isConnectionBased() {
                return true;
            }

        });
        context.setAuthExchange(target, authExchange);

        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenReturn(response1, response2);

        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.singletonList(new BasicScheme()));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Mockito.verify(chain, Mockito.times(2)).proceed(request, scope);
        Mockito.verify(execRuntime).disconnectEndpoint();
        Mockito.verify(inStream2, Mockito.never()).close();

        Assertions.assertNotNull(finalResponse);
        Assertions.assertEquals(200, finalResponse.getCode());
        Assertions.assertNotNull(authExchange.getAuthScheme());
    }

    @Test
    void testExecEntityEnclosingRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = HttpClientContext.create();
        final HttpPost request = new HttpPost("http://foo/test");
        final InputStream inStream0 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        request.setEntity(EntityBuilder.create()
                .setStream(inStream0)
                .build());
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final InputStream inStream1 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        response1.setEntity(EntityBuilder.create()
                .setStream(inStream1)
                .build());

        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());

        Mockito.when(chain.proceed(
                Mockito.same(request),
                Mockito.any())).thenAnswer((Answer<HttpResponse>) invocationOnMock -> {
                    final Object[] args = invocationOnMock.getArguments();
                    final ClassicHttpRequest requestEE = (ClassicHttpRequest) args[0];
                    requestEE.getEntity().writeTo(new ByteArrayOutputStream());
                    return response1;
                });

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse response = protocolExec.execute(request, scope, chain);
        Assertions.assertEquals(401, response.getCode());
    }

    @Test
    void testAuthExchangePathPrefixRestoredAfterChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final ClassicHttpRequest request = new HttpGet("http://foo/blah/a");
        final HttpClientContext context = HttpClientContext.create();

        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());

        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");

        Mockito.when(chain.proceed(Mockito.same(request), Mockito.any()))
                .thenReturn(response1, response2);

        Mockito.when(targetAuthStrategy.select(
                        Mockito.eq(ChallengeType.TARGET),
                        Mockito.any(),
                        Mockito.<HttpClientContext>any()))
                .thenReturn(Collections.singletonList(new BasicScheme()));

        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);

        Assertions.assertEquals(200, finalResponse.getCode());

        final AuthExchange authExchange = context.getAuthExchange(target);
        Assertions.assertEquals("/blah/", authExchange.getPathPrefix());
    }

    @Test
    void testProxyChallengeThroughTunnelIsReturnedToCaller() throws Exception {
        final HttpHost httpsTarget = new HttpHost("https", "foo", 443);
        final HttpHost proxy = new HttpHost("http", "proxy", 8080);
        final HttpRoute route = new HttpRoute(httpsTarget, null, proxy, true,
                RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.LAYERED);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(proxy), "proxyuser", "proxypass".toCharArray())
                .build());
        final ClassicHttpRequest request = new HttpGet("https://foo/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(407, "Huh?");
        response.setHeader(HttpHeaders.PROXY_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");

        Mockito.when(chain.proceed(Mockito.same(request), Mockito.any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Assertions.assertEquals(407, finalResponse.getCode());
        Mockito.verify(chain, Mockito.times(1)).proceed(request, scope);
        Mockito.verifyNoInteractions(proxyAuthStrategy);
        Assertions.assertNull(context.getAuthExchange(proxy).getAuthScheme());
    }

    @Test
    void testProxyChallengeWithoutProxyIsReturnedToCaller() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(target), "user", "pass".toCharArray())
                .build());
        final ClassicHttpRequest request = new HttpGet("http://foo/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(407, "Huh?");
        response.setHeader(HttpHeaders.PROXY_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");

        Mockito.when(chain.proceed(Mockito.same(request), Mockito.any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Assertions.assertEquals(407, finalResponse.getCode());
        Mockito.verify(chain, Mockito.times(1)).proceed(request, scope);
        Mockito.verifyNoInteractions(proxyAuthStrategy);
    }

    @Test
    void testProxyChallengeThroughTunnelWithAuthCacheIsReturnedToCaller() throws Exception {
        final ProtocolExec cachingExec = new ProtocolExec(targetAuthStrategy, proxyAuthStrategy, null, false);
        final HttpHost httpsTarget = new HttpHost("https", "foo", 443);
        final HttpHost proxy = new HttpHost("http", "proxy", 8080);
        final HttpRoute route = new HttpRoute(httpsTarget, null, proxy, true,
                RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.LAYERED);
        final BasicAuthCache authCache = new BasicAuthCache();
        final HttpClientContext context = HttpClientContext.create();
        context.setAuthCache(authCache);
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(proxy), "proxyuser", "proxypass".toCharArray())
                .build());
        final ClassicHttpRequest request = new HttpGet("https://foo/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(407, "Huh?");
        response.setHeader(HttpHeaders.PROXY_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");

        Mockito.when(chain.proceed(Mockito.same(request), Mockito.any())).thenReturn(response);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = cachingExec.execute(request, scope, chain);
        Assertions.assertSame(response, finalResponse);
        Mockito.verify(chain, Mockito.times(1)).proceed(request, scope);
        Mockito.verifyNoInteractions(proxyAuthStrategy);
        Assertions.assertNull(request.getFirstHeader(HttpHeaders.PROXY_AUTHORIZATION));
        Assertions.assertEquals(AuthExchange.State.UNCHALLENGED, context.getAuthExchange(proxy).getState());
        Assertions.assertNull(authCache.get(proxy));
    }

    @Test
    void testProxyChallengeOnPlainRouteStillAuthenticates() throws Exception {
        final HttpHost proxy = new HttpHost("http", "proxy", 8080);
        final HttpRoute route = new HttpRoute(target, null, proxy, false);
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(CredentialsProviderBuilder.create()
                .add(new AuthScope(proxy), "proxyuser", "proxypass".toCharArray())
                .build());
        final ClassicHttpRequest request = new HttpGet("http://foo/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(407, "Huh?");
        response1.setHeader(HttpHeaders.PROXY_AUTHENTICATE, StandardAuthScheme.BASIC + " realm=test");
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");

        Mockito.when(chain.proceed(Mockito.any(), Mockito.any())).thenReturn(response1, response2);
        Mockito.when(proxyAuthStrategy.select(
                Mockito.eq(ChallengeType.PROXY),
                Mockito.any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.singletonList(new BasicScheme()));
        Mockito.when(execRuntime.isConnectionReusable()).thenReturn(true);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, execRuntime, context);
        final ClassicHttpResponse finalResponse = protocolExec.execute(request, scope, chain);
        Assertions.assertEquals(200, finalResponse.getCode());
        final ArgumentCaptor<ClassicHttpRequest> captor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(chain, Mockito.times(2)).proceed(captor.capture(), Mockito.same(scope));
        Assertions.assertNotNull(captor.getAllValues().get(1).getFirstHeader(HttpHeaders.PROXY_AUTHORIZATION));
    }

}
