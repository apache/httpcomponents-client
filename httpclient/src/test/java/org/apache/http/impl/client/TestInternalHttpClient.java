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
package org.apache.http.impl.client;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.cookie.CookieSpecProvider;
import org.apache.http.impl.execchain.ClientExecChain;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 *  Simple tests for {@link InternalHttpClient}.
 */
@SuppressWarnings({"static-access"}) // test code
public class TestInternalHttpClient {

    private ClientExecChain execChain;
    private HttpClientConnectionManager connManager;
    private HttpRoutePlanner routePlanner;
    private Lookup<CookieSpecProvider> cookieSpecRegistry;
    private Lookup<AuthSchemeProvider> authSchemeRegistry;
    private CookieStore cookieStore;
    private CredentialsProvider credentialsProvider;
    private RequestConfig defaultConfig;
    private Closeable closeable1;
    private Closeable closeable2;

    private InternalHttpClient client;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception {
        execChain = Mockito.mock(ClientExecChain.class);
        connManager = Mockito.mock(HttpClientConnectionManager.class);
        routePlanner = Mockito.mock(HttpRoutePlanner.class);
        cookieSpecRegistry = Mockito.mock(Lookup.class);
        authSchemeRegistry = Mockito.mock(Lookup.class);
        cookieStore = Mockito.mock(CookieStore.class);
        credentialsProvider = Mockito.mock(CredentialsProvider.class);
        defaultConfig = RequestConfig.custom().build();
        closeable1 = Mockito.mock(Closeable.class);
        closeable2 = Mockito.mock(Closeable.class);

        client = new InternalHttpClient(execChain, connManager, routePlanner,
                cookieSpecRegistry, authSchemeRegistry, cookieStore, credentialsProvider,
                defaultConfig, Arrays.asList(closeable1, closeable2));

    }

    @Test
    public void testExecute() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 80));

        final ArgumentCaptor<HttpRequestWrapper> argcap = ArgumentCaptor.forClass(HttpRequestWrapper.class);
        Mockito.when(routePlanner.determineRoute(
                Mockito.eq(new HttpHost("somehost")),
                argcap.capture(),
                Mockito.<HttpClientContext>any())).thenReturn(route);

        client.execute(httpget);

        Assert.assertNotNull(argcap.getValue());
        Assert.assertSame(httpget, argcap.getValue().getOriginal());

        Mockito.verify(execChain).execute(
                Mockito.same(route),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.same(httpget));
    }

    @Test(expected=ClientProtocolException.class)
    public void testExecuteHttpException() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
        final HttpRoute route = new HttpRoute(new HttpHost("somehost", 80));

        Mockito.when(routePlanner.determineRoute(
                Mockito.eq(new HttpHost("somehost")),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any())).thenReturn(route);
        Mockito.when(execChain.execute(
                Mockito.same(route),
                Mockito.<HttpRequestWrapper>any(),
                Mockito.<HttpClientContext>any(),
                Mockito.same(httpget))).thenThrow(new HttpException());

        client.execute(httpget);
    }

    @Test
    public void testExecuteDefaultContext() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
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

        final RequestConfig config = RequestConfig.custom().build();
        httpget.setConfig(config);
        final HttpClientContext context = HttpClientContext.create();
        client.execute(httpget, context);

        Assert.assertSame(config, context.getRequestConfig());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExecuteLocalContext() throws Exception {
        final HttpGet httpget = new HttpGet("http://somehost/stuff");
        final HttpClientContext context = HttpClientContext.create();

        final Lookup<CookieSpecProvider> localCookieSpecRegistry = Mockito.mock(Lookup.class);
        final Lookup<AuthSchemeProvider> localAuthSchemeRegistry = Mockito.mock(Lookup.class);
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
