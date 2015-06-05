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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.NonRepeatableRequestException;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.Cancellable;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ConnectionRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.RouteInfo;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.NTLMScheme;
import org.apache.http.impl.conn.ConnectionShutdownException;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.util.EntityUtils;
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
    private HttpRequestExecutor requestExecutor;
    @Mock
    private HttpClientConnectionManager connManager;
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
    private HttpExecutionAware execAware;
    @Mock
    private ConnectionRequest connRequest;
    @Mock
    private HttpClientConnection managedConn;

    private MainClientExec mainClientExec;
    private HttpHost target;
    private HttpHost proxy;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mainClientExec = new MainClientExec(requestExecutor, connManager, reuseStrategy,
            keepAliveStrategy, proxyHttpProcessor, targetAuthStrategy, proxyAuthStrategy, userTokenHandler);
        target = new HttpHost("foo", 80);
        proxy = new HttpHost("bar", 8888);

        Mockito.when(connManager.requestConnection(
                Mockito.<HttpRoute>any(), Mockito.any())).thenReturn(connRequest);
        Mockito.when(connRequest.get(
                Mockito.anyLong(), Mockito.<TimeUnit>any())).thenReturn(managedConn);
        final Map<String, Header> challenges = new HashMap<String, Header>();
        challenges.put("basic", new BasicHeader(AUTH.WWW_AUTH, "Basic realm=test"));
        final AuthOption authOption = new AuthOption(
                new BasicScheme(), new UsernamePasswordCredentials("user:pass"));
        Mockito.when(targetAuthStrategy.getChallenges(
                Mockito.eq(target),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(challenges);
        Mockito.when(targetAuthStrategy.getChallenges(
                Mockito.eq(target),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(challenges);
        Mockito.when(targetAuthStrategy.select(
                Mockito.same(challenges),
                Mockito.eq(target),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(
                new LinkedList<AuthOption>(Arrays.asList(authOption)));
        Mockito.when(proxyAuthStrategy.getChallenges(
                Mockito.eq(proxy),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(challenges);
        Mockito.when(proxyAuthStrategy.getChallenges(
                Mockito.eq(proxy),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(challenges);
        Mockito.when(proxyAuthStrategy.select(
                Mockito.same(challenges),
                Mockito.eq(proxy),
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(
                new LinkedList<AuthOption>(Arrays.asList(authOption)));

    }

    @Test
    public void testExecRequestNonPersistentConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(123)
                .setSocketTimeout(234)
                .setConnectionRequestTimeout(345)
                .build();
        context.setRequestConfig(config);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        final CloseableHttpResponse finalResponse = mainClientExec.execute(
                route, request, context, execAware);
        Mockito.verify(connManager).requestConnection(route, null);
        Mockito.verify(connRequest).get(345, TimeUnit.MILLISECONDS);
        Mockito.verify(execAware, Mockito.times(1)).setCancellable(connRequest);
        Mockito.verify(execAware, Mockito.times(2)).setCancellable(Mockito.<Cancellable>any());
        Mockito.verify(connManager).connect(managedConn, route, 123, context);
        Mockito.verify(connManager).routeComplete(managedConn, route, context);
        Mockito.verify(managedConn).setSocketTimeout(234);
        Mockito.verify(requestExecutor, Mockito.times(1)).execute(request, managedConn, context);
        Mockito.verify(managedConn, Mockito.times(1)).close();
        Mockito.verify(connManager).releaseConnection(managedConn, null, 0, TimeUnit.MILLISECONDS);

        Assert.assertNotNull(context.getTargetAuthState());
        Assert.assertNotNull(context.getProxyAuthState());
        Assert.assertSame(managedConn, context.getConnection());
        Assert.assertNull(context.getUserToken());
        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof HttpResponseProxy);
    }

    @Test
    public void testExecRequestPersistentConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(managedConn.isStale()).thenReturn(Boolean.FALSE);
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(keepAliveStrategy.getKeepAliveDuration(
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(678L);

        final CloseableHttpResponse finalResponse = mainClientExec.execute(
                route, request, context, execAware);
        Mockito.verify(connManager).requestConnection(route, null);
        Mockito.verify(connRequest).get(0, TimeUnit.MILLISECONDS);
        Mockito.verify(requestExecutor, Mockito.times(1)).execute(request, managedConn, context);
        Mockito.verify(connManager).releaseConnection(managedConn, null, 678L, TimeUnit.MILLISECONDS);
        Mockito.verify(managedConn, Mockito.never()).close();

        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof HttpResponseProxy);
    }

    @Test
    public void testExecRequestPersistentStatefulConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(managedConn.isStale()).thenReturn(Boolean.FALSE);
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(userTokenHandler.getUserToken(
                Mockito.<HttpClientContext>any())).thenReturn("this and that");

        mainClientExec.execute(route, request, context, execAware);
        Mockito.verify(connManager).requestConnection(route, null);
        Mockito.verify(connRequest).get(0, TimeUnit.MILLISECONDS);
        Mockito.verify(requestExecutor, Mockito.times(1)).execute(request, managedConn, context);
        Mockito.verify(connManager).releaseConnection(managedConn, "this and that", 0, TimeUnit.MILLISECONDS);
        Mockito.verify(managedConn, Mockito.never()).close();

        Assert.assertEquals("this and that", context.getUserToken());
    }

    @Test
    public void testExecRequestConnectionRelease() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        // The entity is streaming
        response.setEntity(EntityBuilder.create()
                .setStream(new ByteArrayInputStream(new byte[]{}))
                .build());

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(managedConn.isStale()).thenReturn(Boolean.FALSE);
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.FALSE);

        final CloseableHttpResponse finalResponse = mainClientExec.execute(
                route, request, context, execAware);
        Mockito.verify(connManager).requestConnection(route, null);
        Mockito.verify(connRequest).get(0, TimeUnit.MILLISECONDS);
        Mockito.verify(requestExecutor, Mockito.times(1)).execute(request, managedConn, context);
        Mockito.verify(connManager, Mockito.never()).releaseConnection(
                Mockito.same(managedConn),
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.<TimeUnit>any());
        Mockito.verify(managedConn, Mockito.never()).close();

        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof HttpResponseProxy);
        finalResponse.close();

        Mockito.verify(connManager, Mockito.times(1)).releaseConnection(
                managedConn, null, 0, TimeUnit.MILLISECONDS);
        Mockito.verify(managedConn, Mockito.times(1)).close();
    }

    @Test
    public void testSocketTimeoutExistingConnection() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final RequestConfig config = RequestConfig.custom().setSocketTimeout(3000).build();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        context.setRequestConfig(config);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(managedConn.isOpen()).thenReturn(true);
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        mainClientExec.execute(route, request, context, execAware);
        Mockito.verify(managedConn).setSocketTimeout(3000);
    }

    @Test
    public void testSocketTimeoutReset() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        mainClientExec.execute(route, request, context, execAware);
        Mockito.verify(managedConn, Mockito.never()).setSocketTimeout(Mockito.anyInt());
    }

    @Test(expected=RequestAbortedException.class)
    public void testExecAbortedPriorToConnectionLease() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.FALSE);
        Mockito.when(execAware.isAborted()).thenReturn(Boolean.TRUE);
        try {
            mainClientExec.execute(route, request, context, execAware);
        } catch (final IOException ex) {
            Mockito.verify(connRequest, Mockito.times(1)).cancel();
            throw ex;
        }
    }

    @Test(expected=RequestAbortedException.class)
    public void testExecAbortedPriorToConnectionSetup() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.FALSE);
        Mockito.when(execAware.isAborted()).thenReturn(Boolean.FALSE, Boolean.TRUE);
        try {
            mainClientExec.execute(route, request, context, execAware);
        } catch (final IOException ex) {
            Mockito.verify(connRequest, Mockito.times(1)).get(0, TimeUnit.MILLISECONDS);
            Mockito.verify(execAware, Mockito.times(2)).setCancellable(Mockito.<Cancellable>any());
            Mockito.verify(connManager, Mockito.never()).connect(
                    Mockito.same(managedConn),
                    Mockito.<HttpRoute>any(),
                    Mockito.anyInt(),
                    Mockito.<HttpContext>any());
            throw ex;
        }
    }

    @Test(expected=RequestAbortedException.class)
    public void testExecAbortedPriorToRequestExecution() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.FALSE);
        Mockito.when(execAware.isAborted()).thenReturn(Boolean.FALSE, Boolean.FALSE, Boolean.TRUE);
        try {
            mainClientExec.execute(route, request, context, execAware);
        } catch (final IOException ex) {
            Mockito.verify(connRequest, Mockito.times(1)).get(0, TimeUnit.MILLISECONDS);
            Mockito.verify(connManager, Mockito.times(1)).connect(managedConn, route, 0, context);
            Mockito.verify(requestExecutor, Mockito.never()).execute(
                    Mockito.same(request),
                    Mockito.<HttpClientConnection>any(),
                    Mockito.<HttpClientContext>any());
            throw ex;
        }
    }

    @Test(expected=RequestAbortedException.class)
    public void testExecConnectionRequestFailed() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(connRequest.get(Mockito.anyInt(), Mockito.<TimeUnit>any()))
                .thenThrow(new ExecutionException("Opppsie", null));
        mainClientExec.execute(route, request, context, execAware);
    }

    @Test
    public void testExecRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 401, "Huh?");
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());
        final HttpResponse response2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final InputStream instream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        response2.setEntity(EntityBuilder.create()
                .setStream(instream2)
                .build());

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(managedConn.isStale()).thenReturn(Boolean.FALSE);
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response1, response2);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(targetAuthStrategy.isAuthenticationRequested(
                Mockito.eq(target),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);

        final CloseableHttpResponse finalResponse = mainClientExec.execute(
                route, request, context, execAware);
        Mockito.verify(requestExecutor, Mockito.times(2)).execute(request, managedConn, context);
        Mockito.verify(instream1).close();
        Mockito.verify(instream2, Mockito.never()).close();

        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(200, finalResponse.getStatusLine().getStatusCode());
    }

    @Test
    public void testExecEntityEnclosingRequestRetryOnAuthChallenge() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 401, "Huh?");
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());
        final HttpResponse response2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        final InputStream instream2 = Mockito.spy(new ByteArrayInputStream(new byte[] {2, 3, 4}));
        response2.setEntity(EntityBuilder.create()
                .setStream(instream2)
                .build());

        final AuthState proxyAuthState = new AuthState();
        proxyAuthState.setState(AuthProtocolState.SUCCESS);
        proxyAuthState.update(new NTLMScheme(), new NTCredentials("user:pass"));

        final HttpClientContext context = new HttpClientContext();
        context.setAttribute(HttpClientContext.PROXY_AUTH_STATE, proxyAuthState);

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(managedConn.isStale()).thenReturn(Boolean.FALSE);
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response1, response2);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.FALSE);
        Mockito.when(targetAuthStrategy.isAuthenticationRequested(
                Mockito.eq(target),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);

        final CloseableHttpResponse finalResponse = mainClientExec.execute(
                route, request, context, execAware);
        Mockito.verify(requestExecutor, Mockito.times(2)).execute(request, managedConn, context);
        Mockito.verify(managedConn).close();
        Mockito.verify(instream2, Mockito.never()).close();

        Assert.assertNotNull(finalResponse);
        Assert.assertEquals(200, finalResponse.getStatusLine().getStatusCode());
        Assert.assertNull(proxyAuthState.getAuthScheme());
        Assert.assertNull(proxyAuthState.getCredentials());
    }

    @Test(expected = NonRepeatableRequestException.class)
    public void testExecEntityEnclosingRequest() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpPost post = new HttpPost("http://bar/test");
        final InputStream instream0 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        post.setEntity(EntityBuilder.create()
                .setStream(instream0)
                .build());
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(post);

        final HttpResponse response1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 401, "Huh?");
        final InputStream instream1 = new ByteArrayInputStream(new byte[] {1, 2, 3});
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(managedConn.isStale()).thenReturn(Boolean.FALSE);
        Mockito.when(requestExecutor.execute(
                Mockito.same(request),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenAnswer(new Answer<HttpResponse>() {

            @Override
            public HttpResponse answer(final InvocationOnMock invocationOnMock) throws Throwable {
                final Object[] args = invocationOnMock.getArguments();
                final HttpEntityEnclosingRequest requestEE = (HttpEntityEnclosingRequest) args[0];
                requestEE.getEntity().writeTo(new ByteArrayOutputStream());
                return response1;
            }

        });
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(targetAuthStrategy.isAuthenticationRequested(
                Mockito.eq(target),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);

        mainClientExec.execute(route, request, context, execAware);
    }

    @Test(expected=InterruptedIOException.class)
    public void testExecConnectionShutDown() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenThrow(new ConnectionShutdownException());

        mainClientExec.execute(route, request, context, execAware);
    }

    @Test(expected=RuntimeException.class)
    public void testExecRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenThrow(new RuntimeException("Ka-boom"));

        try {
            mainClientExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(connManager).releaseConnection(managedConn, null, 0, TimeUnit.MILLISECONDS);

            throw ex;
        }
    }

    @Test(expected=HttpException.class)
    public void testExecHttpException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenThrow(new HttpException("Ka-boom"));

        try {
            mainClientExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(connManager).releaseConnection(managedConn, null, 0, TimeUnit.MILLISECONDS);

            throw ex;
        }
    }

    @Test(expected=IOException.class)
    public void testExecIOException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenThrow(new IOException("Ka-boom"));

        try {
            mainClientExec.execute(route, request, context, execAware);
        } catch (final Exception ex) {
            Mockito.verify(connManager).releaseConnection(managedConn, null, 0, TimeUnit.MILLISECONDS);

            throw ex;
        }
    }

    @Test
    public void testEstablishDirectRoute() throws Exception {
        final AuthState authState = new AuthState();
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);

        mainClientExec.establishRoute(authState, managedConn, route, request, context);

        Mockito.verify(connManager).connect(managedConn, route, 0, context);
        Mockito.verify(connManager).routeComplete(managedConn, route, context);
    }

    @Test
    public void testEstablishRouteDirectProxy() throws Exception {
        final AuthState authState = new AuthState();
        final HttpRoute route = new HttpRoute(target, null, proxy, false);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);

        mainClientExec.establishRoute(authState, managedConn, route, request, context);

        Mockito.verify(connManager).connect(managedConn, route, 0, context);
        Mockito.verify(connManager).routeComplete(managedConn, route, context);
    }

    @Test
    public void testEstablishRouteViaProxyTunnel() throws Exception {
        final AuthState authState = new AuthState();
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(321)
                .build();
        context.setRequestConfig(config);
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        mainClientExec.establishRoute(authState, managedConn, route, request, context);

        Mockito.verify(connManager).connect(managedConn, route, 321, context);
        Mockito.verify(connManager).routeComplete(managedConn, route, context);
        final ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(requestExecutor).execute(
                reqCaptor.capture(),
                Mockito.same(managedConn),
                Mockito.same(context));
        final HttpRequest connect = reqCaptor.getValue();
        Assert.assertNotNull(connect);
        Assert.assertEquals("CONNECT", connect.getRequestLine().getMethod());
        Assert.assertEquals(HttpVersion.HTTP_1_1, connect.getRequestLine().getProtocolVersion());
        Assert.assertEquals("foo:80", connect.getRequestLine().getUri());
    }

    @Test(expected = HttpException.class)
    public void testEstablishRouteViaProxyTunnelUnexpectedResponse() throws Exception {
        final AuthState authState = new AuthState();
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 101, "Lost");

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        mainClientExec.establishRoute(authState, managedConn, route, request, context);
    }

    @Test(expected = HttpException.class)
    public void testEstablishRouteViaProxyTunnelFailure() throws Exception {
        final AuthState authState = new AuthState();
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 500, "Boom");
        response.setEntity(new StringEntity("Ka-boom"));

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response);

        try {
            mainClientExec.establishRoute(authState, managedConn, route, request, context);
        } catch (final TunnelRefusedException ex) {
            final HttpResponse r = ex.getResponse();
            Assert.assertEquals("Ka-boom", EntityUtils.toString(r.getEntity()));

            Mockito.verify(managedConn).close();

            throw ex;
        }
    }

    @Test
    public void testEstablishRouteViaProxyTunnelRetryOnAuthChallengePersistentConnection() throws Exception {
        final AuthState authState = new AuthState();
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 401, "Huh?");
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());
        final HttpResponse response2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(proxyAuthStrategy.isAuthenticationRequested(
                Mockito.eq(proxy),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response1, response2);

        mainClientExec.establishRoute(authState, managedConn, route, request, context);

        Mockito.verify(connManager).connect(managedConn, route, 0, context);
        Mockito.verify(connManager).routeComplete(managedConn, route, context);
        Mockito.verify(instream1).close();
    }

    @Test
    public void testEstablishRouteViaProxyTunnelRetryOnAuthChallengeNonPersistentConnection() throws Exception {
        final AuthState authState = new AuthState();
        final HttpRoute route = new HttpRoute(target, null, proxy, true);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));
        final HttpResponse response1 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 401, "Huh?");
        final InputStream instream1 = Mockito.spy(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        response1.setEntity(EntityBuilder.create()
                .setStream(instream1)
                .build());
        final HttpResponse response2 = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);
        Mockito.when(proxyAuthStrategy.isAuthenticationRequested(
                Mockito.eq(proxy),
                Mockito.same(response1),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.TRUE);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.<HttpResponse>any(),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.FALSE);
        Mockito.when(requestExecutor.execute(
                Mockito.<HttpRequest>any(),
                Mockito.<HttpClientConnection>any(),
                Mockito.<HttpClientContext>any())).thenReturn(response1, response2);

        mainClientExec.establishRoute(authState, managedConn, route, request, context);

        Mockito.verify(connManager).connect(managedConn, route, 0, context);
        Mockito.verify(connManager).routeComplete(managedConn, route, context);
        Mockito.verify(instream1, Mockito.never()).close();
        Mockito.verify(managedConn).close();
    }

    @Test(expected = HttpException.class)
    public void testEstablishRouteViaProxyTunnelMultipleHops() throws Exception {
        final AuthState authState = new AuthState();
        final HttpHost proxy1 = new HttpHost("this", 8888);
        final HttpHost proxy2 = new HttpHost("that", 8888);
        final HttpRoute route = new HttpRoute(target, null, new HttpHost[] {proxy1, proxy2},
                true, RouteInfo.TunnelType.TUNNELLED, RouteInfo.LayerType.LAYERED);
        final HttpClientContext context = new HttpClientContext();
        final HttpRequestWrapper request = HttpRequestWrapper.wrap(new HttpGet("http://bar/test"));

        Mockito.when(managedConn.isOpen()).thenReturn(Boolean.TRUE);

        mainClientExec.establishRoute(authState, managedConn, route, request, context);
    }

}