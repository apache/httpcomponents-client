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

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.CookieSpecFactory;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Lookup;
import org.apache.hc.core5.http.impl.io.HttpRequestExecutor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 *  Simple tests for {@link InternalHttpClient}.
 */
@SuppressWarnings({"static-access"}) // test code
public class TestInternalHttpClient {

    @Mock
    private HttpClientConnectionManager connManager;
    @Mock
    private HttpRequestExecutor requestExecutor;
    @Mock
    private ExecChainHandler execChain;
    @Mock
    private HttpRoutePlanner routePlanner;
    @Mock
    private Lookup<CookieSpecFactory> cookieSpecRegistry;
    @Mock
    private Lookup<AuthSchemeFactory> authSchemeRegistry;
    @Mock
    private CookieStore cookieStore;
    @Mock
    private CredentialsProvider credentialsProvider;
    @Mock
    private RequestConfig defaultConfig;
    @Mock
    private Closeable closeable1;
    @Mock
    private Closeable closeable2;

    private InternalHttpClient client;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        client = new InternalHttpClient(connManager, requestExecutor, new ExecChainElement(execChain, null), routePlanner,
                cookieSpecRegistry, authSchemeRegistry, cookieStore, credentialsProvider,
                defaultConfig, Arrays.asList(closeable1, closeable2));

    }

    @Test
    public void testExecute() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 80));

        Mockito.when(routePlanner.determineRoute(
                Mockito.eq(new HttpHost("somehost")),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        client.execute(httpget);

        Mockito.verify(execChain).execute(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any(),
                Mockito.<ExecChain>any());
    }

    @Test(expected=ClientProtocolException.class)
    public void testExecuteHttpException() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 80));

        Mockito.when(routePlanner.determineRoute(
                Mockito.eq(new HttpHost("somehost")),
                Mockito.<HttpClientContext>any())).thenReturn(route);
        Mockito.when(execChain.execute(
                Mockito.<ClassicHttpRequest>any(),
                Mockito.<ExecChain.Scope>any(),
                Mockito.<ExecChain>any())).thenThrow(new HttpException());

        client.execute(httpget);
    }

    @Test
    public void testExecuteDefaultContext() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 80));

        Mockito.when(routePlanner.determineRoute(
                Mockito.eq(new HttpHost("somehost")),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        final HttpClientContext context = HttpClientContext.create();
        client.execute(httpget, context);

        Assert.assertSame(cookieSpecRegistry, context.getCookieSpecRegistry());
        Assert.assertSame(authSchemeRegistry, context.getAuthSchemeRegistry());
        Assert.assertSame(cookieStore, context.getCookieStore());
        Assert.assertSame(credentialsProvider, context.getCredentialsProvider());
        Assert.assertSame(defaultConfig, context.getRequestConfig());
    }

    @Test
    public void testExecuteRequestConfig() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 80));

        Mockito.when(routePlanner.determineRoute(
                Mockito.eq(new HttpHost("somehost")),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        final RequestConfig config = RequestConfig.custom().build();
        httpget.setConfig(config);
        final HttpClientContext context = HttpClientContext.create();
        client.execute(httpget, context);

        Assert.assertSame(config, context.getRequestConfig());
    }

    @Test
    public void testExecuteLocalContext() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 80));

        Mockito.when(routePlanner.determineRoute(
                Mockito.eq(new HttpHost("somehost")),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        final HttpClientContext context = HttpClientContext.create();

        final Lookup<CookieSpecFactory> localCookieSpecRegistry = Mockito.mock(Lookup.class);
        final Lookup<AuthSchemeFactory> localAuthSchemeRegistry = Mockito.mock(Lookup.class);
        final CookieStore localCookieStore = Mockito.mock(CookieStore.class);
        final CredentialsProvider localCredentialsProvider = Mockito.mock(CredentialsProvider.class);
        final RequestConfig localConfig = RequestConfig.custom().build();

        context.setCookieSpecRegistry(localCookieSpecRegistry);
        context.setAuthSchemeRegistry(localAuthSchemeRegistry);
        context.setCookieStore(localCookieStore);
        context.setCredentialsProvider(localCredentialsProvider);
        context.setRequestConfig(localConfig);

        client.execute(httpget, context);

        Assert.assertSame(localCookieSpecRegistry, context.getCookieSpecRegistry());
        Assert.assertSame(localAuthSchemeRegistry, context.getAuthSchemeRegistry());
        Assert.assertSame(localCookieStore, context.getCookieStore());
        Assert.assertSame(localCredentialsProvider, context.getCredentialsProvider());
        Assert.assertSame(localConfig, context.getRequestConfig());
    }

    @Test
    public void testClientClose() throws Exception {
        client.close();

        Mockito.verify(closeable1).close();
        Mockito.verify(closeable2).close();
    }

    @Test
    public void testClientCloseIOException() throws Exception {
        Mockito.doThrow(new IOException()).when(closeable1).close();

        client.close();

        Mockito.verify(closeable1).close();
        Mockito.verify(closeable2).close();
    }

}
