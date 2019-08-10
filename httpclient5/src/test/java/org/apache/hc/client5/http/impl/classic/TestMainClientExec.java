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
import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.UserTokenHandler;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecRuntime;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.entity.EntityBuilder;
import org.apache.hc.client5.http.impl.ConnectionShutdownException;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionReuseStrategy;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
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
public class TestMainClientExec {

    @Mock
    private HttpClientConnectionManager connectionManager;
    @Mock
    private ConnectionReuseStrategy reuseStrategy;
    @Mock
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    @Mock
    private UserTokenHandler userTokenHandler;
    @Mock
    private ExecRuntime endpoint;

    private MainClientExec mainClientExec;
    private HttpHost target;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mainClientExec = new MainClientExec(connectionManager, reuseStrategy, keepAliveStrategy, userTokenHandler);
        target = new HttpHost("foo", 80);
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

        Mockito.when(endpoint.isEndpointAcquired()).thenReturn(false);
        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(false);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);
        Mockito.verify(endpoint).execute("test", request, context);
        Mockito.verify(endpoint, Mockito.times(1)).markConnectionNonReusable();
        Mockito.verify(endpoint, Mockito.never()).releaseEndpoint();

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

        Mockito.when(endpoint.isEndpointAcquired()).thenReturn(false);
        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(false);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);

        Mockito.verify(endpoint).execute("test", request, context);
        Mockito.verify(endpoint).markConnectionNonReusable();
        Mockito.verify(endpoint).releaseEndpoint();

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
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connectEndpoint(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(true);
        Mockito.when(keepAliveStrategy.getKeepAliveDuration(
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(TimeValue.ofMilliseconds(678L));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);

        Mockito.verify(endpoint).execute("test", request, context);
        Mockito.verify(endpoint).markConnectionReusable(null, TimeValue.ofMilliseconds(678L));
        Mockito.verify(endpoint, Mockito.never()).releaseEndpoint();

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
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connectEndpoint(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(true);
        Mockito.when(keepAliveStrategy.getKeepAliveDuration(
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(TimeValue.ofMilliseconds(678L));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);

        Mockito.verify(endpoint).execute("test", request, context);
        Mockito.verify(endpoint).releaseEndpoint();

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
        Mockito.doAnswer(connectionState.connectAnswer()).when(endpoint).connectEndpoint(Mockito.<HttpClientContext>any());
        Mockito.when(endpoint.isEndpointConnected()).thenAnswer(connectionState.isConnectedAnswer());
        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenReturn(response);
        Mockito.when(reuseStrategy.keepAlive(
                Mockito.same(request),
                Mockito.same(response),
                Mockito.<HttpClientContext>any())).thenReturn(Boolean.FALSE);

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        final ClassicHttpResponse finalResponse = mainClientExec.execute(request, scope, null);
        Mockito.verify(endpoint, Mockito.times(1)).execute("test", request, context);
        Mockito.verify(endpoint, Mockito.never()).disconnectEndpoint();
        Mockito.verify(endpoint, Mockito.never()).releaseEndpoint();

        Assert.assertNotNull(finalResponse);
        Assert.assertTrue(finalResponse instanceof CloseableHttpResponse);
        finalResponse.close();

        Mockito.verify(endpoint).disconnectEndpoint();
        Mockito.verify(endpoint).discardEndpoint();
    }

    @Test(expected=InterruptedIOException.class)
    public void testExecConnectionShutDown() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenThrow(new ConnectionShutdownException());

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        try {
            mainClientExec.execute(request, scope, null);
        } catch (final Exception ex) {
            Mockito.verify(endpoint).discardEndpoint();
            throw ex;
        }
    }

    @Test(expected=RuntimeException.class)
    public void testExecRuntimeException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenThrow(new RuntimeException("Ka-boom"));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        try {
            mainClientExec.execute(request, scope, null);
        } catch (final Exception ex) {
            Mockito.verify(endpoint).discardEndpoint();
            throw ex;
        }
    }

    @Test(expected=HttpException.class)
    public void testExecHttpException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenThrow(new HttpException("Ka-boom"));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        try {
            mainClientExec.execute(request, scope, null);
        } catch (final Exception ex) {
            Mockito.verify(endpoint).discardEndpoint();
            throw ex;
        }
    }

    @Test(expected=IOException.class)
    public void testExecIOException() throws Exception {
        final HttpRoute route = new HttpRoute(target);
        final HttpClientContext context = new HttpClientContext();
        final ClassicHttpRequest request = new HttpGet("http://bar/test");

        Mockito.when(endpoint.execute(
                Mockito.anyString(),
                Mockito.same(request),
                Mockito.<HttpClientContext>any())).thenThrow(new IOException("Ka-boom"));

        final ExecChain.Scope scope = new ExecChain.Scope("test", route, request, endpoint, context);
        try {
            mainClientExec.execute(request, scope, null);
        } catch (final Exception ex) {
            Mockito.verify(endpoint).discardEndpoint();
            throw ex;
        }
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

        }
    }

}
