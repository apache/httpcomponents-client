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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.Map;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.RouteInfo;
import org.apache.hc.client5.http.auth.AuthChallenge;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScheme;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.ChallengeType;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.NTLMScheme;
import org.apache.hc.client5.http.protocol.AuthenticationStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.NonRepeatableRequestException;
import org.apache.hc.client5.http.protocol.UserTokenHandler;
import org.apache.hc.client5.http.sync.ExecChain;
import org.apache.hc.client5.http.sync.ExecRuntime;
import org.apache.hc.client5.http.sync.methods.HttpGet;
import org.apache.hc.client5.http.sync.methods.HttpPost;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpProcessor;
import org.apache.hc.core5.util.TimeValue;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@SuppressWarnings({"boxing","static-access"}) // test code
public class TestMainClientExec {

    @Mock
    private ConnectionReuseStrategy reuseStrategy;
    @Mock
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    @Mock
    private HttpProcessor proxyHttpProcessor;
    @Mock
    private AuthenticationStrategy targetAuthStrategy;
    @Mock
    private AuthenticationStrategy proxyAuthStrategy;
    @Mock
    private UserTokenHandler userTokenHandler;
    @Mock
    private ExecRuntime endpoint;

    private MainClientExec mainClientExec;
    private HttpHost target;
    private HttpHost proxy;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mainClientExec = new MainClientExec(reuseStrategy, keepAliveStrategy, proxyHttpProcessor, targetAuthStrategy,
                proxyAuthStrategy, userTokenHandler);
        target = new HttpHost("foo", 80);
        proxy = new HttpHost("bar", 8888);
    }

    @Test
    public void testExecRequestNonPersistentConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.setEntity(EntityBuilder.create()
                .setStream(new ByteArrayInputStream(new byte[]{}))
                .build());

        Mockito.when(endpoint.isConnectionAcquired()).thenReturn(false);
        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(false);

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);
        Mockito.verify(endpoint).acquireConnection(route, null, context);
        Mockito.verify(endpoint).connect(context);
        Mockito.verify(endpoint).execute(request, context);
        Mockito.verify(endpoint, Mockito.times(1)).markConnectionNonReusable();
        Mockito.verify(endpoint, Mockito.never()).releaseConnection();

        Assert.assertNull(context.getUserToken());
        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof CloseableHttpResponse);
    }

    @Test
    public void testExecRequestNonPersistentConnectionNoResponseEntity() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        response.setEntity(null);

        Mockito.when(endpoint.isConnectionAcquired()).thenReturn(false);
        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(false);

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);

        Mockito.verify(endpoint).acquireConnection(route, null, context);
        Mockito.verify(endpoint).connect(context);
        Mockito.verify(endpoint).execute(request, context);
        Mockito.verify(endpoint).markConnectionNonReusable();
        Mockito.verify(endpoint).releaseConnection();

        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof CloseableHttpResponse);
    }

    @Test
    public void testExecRequestPersistentConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        // The entity is streaming
        response.setEntity(EntityBuilder.create()
                .setStream(new ByteArrayInputStream(new byte[]{}))
                .build());

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(true);
        Mockito.when(keepAliveStrategy.getKeepAliveDuration(
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(TimeValue.ofMillis(678L));

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);

        Mockito.verify(endpoint).acquireConnection(route, null, context);
        Mockito.verify(endpoint).connect(context);
        Mockito.verify(endpoint).execute(request, context);
        Mockito.verify(endpoint).markConnectionReusable();
        Mockito.verify(endpoint, Mockito.never()).releaseConnection();

        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof CloseableHttpResponse);
    }

    @Test
    public void testExecRequestPersistentConnectionNoResponseEntity() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(true);
        Mockito.when(keepAliveStrategy.getKeepAliveDuration(
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(TimeValue.ofMillis(678L));

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);

        Mockito.verify(endpoint).acquireConnection(route, null, context);
        Mockito.verify(endpoint).connect(context);
        Mockito.verify(endpoint).execute(request, context);
        Mockito.verify(endpoint).releaseConnection();

        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof CloseableHttpResponse);
    }

    @Test
    public void testExecRequestConnectionRelease() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");
        // The entity is streaming
        response.setEntity(EntityBuilder.create()
                .setStream(new ByteArrayInputStream(new byte[]{}))
                .build());

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.FALSE);

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);
        Mockito.verify(endpoint, Mockito.times(1)).execute(request, context);
        Mockito.verify(endpoint, Mockito.never()).disconnect();
        Mockito.verify(endpoint, Mockito.never()).releaseConnection();

        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof CloseableHttpResponse);
        finalResponse.close();

        Mockito.verify(endpoint).disconnect();
        Mockito.verify(endpoint).discardConnection();
    }

    @Test(expected=InterruptedIOException.class)
    public void testExecConnectionShutDown() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenThrow(new ConnectionShutdownException());

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        try {
            mainClientExec.execute(request, scope, null);
        } catch (Exception ex) {
            Mockito.verify(endpoint).discardConnection();
            throw ex;
        }
    }

    @Test(expected=RuntimeException.class)
    public void testExecRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenThrow(new RuntimeException("Ka-boom"));

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        try {
            mainClientExec.execute(request, scope, null);
        } catch (final Exception ex) {
            Mockito.verify(endpoint).discardConnection();
            throw ex;
        }
    }

    @Test(expected=HttpException.class)
    public void testExecHttpException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenThrow(new HttpException("Ka-boom"));

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        try {
            mainClientExec.execute(request, scope, null);
        } catch (final Exception ex) {
            Mockito.verify(endpoint).discardConnection();
            throw ex;
        }
    }

    @Test(expected=IOException.class)
    public void testExecIOException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenThrow(new IOException("Ka-boom"));

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        try {
            mainClientExec.execute(request, scope, null);
        } catch (final Exception ex) {
            Mockito.verify(endpoint).discardConnection();
            throw ex;
        }
    }

    @Test
    public void testExecRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://foo/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=test");
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");
        final InputStream instream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        response2.setEntity(EntityBuilder.create()
                .setStream(instream2)
                .build());

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(target), new UsernamePasswordCredentials("user", "pass".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response1, response2);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.<Map<String, AuthChallenge>>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.<AuthScheme>singletonList(new BasicScheme()));
        Mockito.when(endpoint.isConnectionReusable()).thenReturn(true);

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);
        Mockito.verify(endpoint, Mockito.times(2)).execute(request, context);
        Mockito.verify(instream1).close();
        Mockito.verify(instream2, Mockito.never()).close();

        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(200, finalResponse.getCode());
    }

    @Test
    public void testExecEntityEnclosingRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target, proxy);
        final ClassicHttpRequest request = new HttpGet("http://foo/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=test");
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");
        final InputStream instream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        response2.setEntity(EntityBuilder.create()
                .setStream(instream2)
                .build());

        final HttpClientContext context = new HttpClientContext();

        final AuthExchange proxyAuthExchange = new AuthExchange();
        proxyAuthExchange.setState(AuthExchange.State.SUCCESS);
        proxyAuthExchange.select(new NTLMScheme());
        context.setAuthExchange(proxy, proxyAuthExchange);

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(target), new UsernamePasswordCredentials("user", "pass".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response1, response2);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.FALSE);

        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.<Map<String, AuthChallenge>>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.<AuthScheme>singletonList(new BasicScheme()));

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);
        Mockito.verify(endpoint, Mockito.times(2)).execute(request, context);
        Mockito.verify(endpoint).disconnect();
        Mockito.verify(instream2, Mockito.never()).close();

        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(200, finalResponse.getCode());
        Assert.assertNull(proxyAuthExchange.getAuthScheme());
    }

    @Test(expected = NonRepeatableRequestException.class)
    public void testExecEntityEnclosingRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpPost request = new HttpPost("http://foo/test");
        final InputStream instream0 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        request.setEntity(EntityBuilder.create()
                .setStream(instream0)
                .build());
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(401, "Huh?");
        response1.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=test");
        final InputStream instream1 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(target), new UsernamePasswordCredentials("user", "pass".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenAnswer(new Answer<HttpResponse>() {

            @Override
            public HttpResponse answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final Object[] args = invocationOnMock.getArguments();
                final ClassicHttpRequest requestEE = (ClassicHttpRequest) args[0];
                requestEE.getEntity().writeTo(new ByteArrayOutputStream());
                return response1;
            }

        });
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);

        Mockito.when(targetAuthStrategy.select(
                Mockito.eq(ChallengeType.TARGET),
                Mockito.<Map<String, AuthChallenge>>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.<AuthScheme>singletonList(new BasicScheme()));

        final ExecChain.Scope scope = new ExecChain.Scope(route, request, endpoint, context);
        mainClientExec.execute(request, scope, null);
    }

    @Test
    public void testEstablishDirectRoute() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());

        mainClientExec.establishRoute(route, request, endpoint, context);

        Mockito.verify(endpoint).connect(context);
        Mockito.verify(endpoint, Mockito.never()).execute(Mockito.<ClassicHttpRequest>any(), Mockito.<HttpClientContext>any());
    }

    @Test
    public void testEstablishRouteDirectProxy() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, false);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());

        mainClientExec.establishRoute(route, request, endpoint, context);

        Mockito.verify(endpoint).connect(context);
        Mockito.verify(endpoint, Mockito.never()).execute(Mockito.<ClassicHttpRequest>any(), Mockito.<HttpClientContext>any());
    }

    @Test
    public void testEstablishRouteViaProxyTunnel() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(200, "OK");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        mainClientExec.establishRoute(route, request, endpoint, context);

        Mockito.verify(endpoint).connect(context);
        final ArgumentCaptor<ClassicHttpRequest> reqCaptor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(endpoint).execute(
                reqCaptor.capture(),
                Mockito.same(context));
        final HttpRequest connect = reqCaptor.getValue();
        Assert.assertNotNull(connect);
        Assert.assertEquals("CONNECT", connect.getMethod());
        Assert.assertEquals(HttpVersion.HTTP_1_1, connect.getVersion());
        Assert.assertEquals("foo:80", connect.getRequestUri());
    }

    @Test(expected = HttpException.class)
    public void testEstablishRouteViaProxyTunnelUnexpectedResponse() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(101, "Lost");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        mainClientExec.establishRoute(route, request, endpoint, context);
    }

    @Test(expected = HttpException.class)
    public void testEstablishRouteViaProxyTunnelFailure() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response = new BasicClassicHttpResponse(500, "Boom");
        response.setEntity(new StringEntity("Ka-boom"));

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        try {
            mainClientExec.establishRoute(route, request, endpoint, context);
        } catch (final TunnelRefusedException ex) {
            final ClassicHttpResponse r = ex.getResponse();
            Assert.assertEquals("Ka-boom", EntityUtils.toString(r.getEntity()));
            Mockito.verify(endpoint).disconnect();
            Mockito.verify(endpoint).discardConnection();
            throw ex;
        }
    }

    @Test
    public void testEstablishRouteViaProxyTunnelRetryOnAuthChallengePersistentConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(407, "Huh?");
        response1.setHeader(HttpHeaders.PROXY_AUTHENTICATE, "Basic realm=test");
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxy), new UsernamePasswordCredentials("user", "pass".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(endpoint.execute(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response1, response2);

        Mockito.when(proxyAuthStrategy.select(
                Mockito.eq(ChallengeType.PROXY),
                Mockito.<Map<String, AuthChallenge>>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.<AuthScheme>singletonList(new BasicScheme()));

        mainClientExec.establishRoute(route, request, endpoint, context);

        Mockito.verify(endpoint).connect(context);
        Mockito.verify(instream1).close();
    }

    @Test
    public void testEstablishRouteViaProxyTunnelRetryOnAuthChallengeNonPersistentConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");
        final ClassicHttpResponse response1 = new BasicClassicHttpResponse(407, "Huh?");
        response1.setHeader(HttpHeaders.PROXY_AUTHENTICATE, "Basic realm=test");
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());
        final ClassicHttpResponse response2 = new BasicClassicHttpResponse(200, "OK");

        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxy), new UsernamePasswordCredentials("user", "pass".toCharArray()));
        context.setCredentialsProvider(credentialsProvider);

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.FALSE);
        Mockito.when(endpoint.execute(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response1, response2);

        Mockito.when(proxyAuthStrategy.select(
                Mockito.eq(ChallengeType.PROXY),
                Mockito.<Map<String, AuthChallenge>>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Collections.<AuthScheme>singletonList(new BasicScheme()));

        mainClientExec.establishRoute(route, request, endpoint, context);

        Mockito.verify(endpoint).connect(context);
        Mockito.verify(instream1, Mockito.never()).close();
        Mockito.verify(endpoint).disconnect();
    }

    @Test(expected = HttpException.class)
    public void testEstablishRouteViaProxyTunnelMultipleHops() throws Exception {
        final HttpHost proxy1 = new HttpHost("this", 8888);
        final HttpHost proxy2 = new HttpHost("that", 8888);
        final HttpRoute route = new HttpRoute(target, null, new HttpHost[] {proxy1, proxy2},
                true, RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.LAYERED);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        final ConnectionState connectionState = new ConnectionState();
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connect(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isConnected()).thenAnswer(connectionState.isConnectedAnswer());

        mainClientExec.establishRoute(route, request, endpoint, context);
    }

    static class ConnectionState {

        private boolean connected;

        public Answer connectAnswer() {

            return new Answer() {

                @Override
                public Object answer(final InvocationOnMock invocationOnMock) throws Throwable {
                    connected = true;
                    return null;
                }

            };
        }

        public Answer<Boolean> isConnectedAnswer() {

            return new Answer<Boolean>() {

                @Override
                public Boolean answer(final InvocationOnMock invocationOnMock) throws Throwable {
                    return connected;
                }

            };

        };
    }

}
