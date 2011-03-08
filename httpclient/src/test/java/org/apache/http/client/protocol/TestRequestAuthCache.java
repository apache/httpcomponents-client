/*
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.client.protocol;

import junit.framework.Assert;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;

public class TestRequestAuthCache {

    private HttpHost target;
    private HttpHost proxy;
    private Credentials creds1;
    private Credentials creds2;
    private AuthScope authscope1;
    private AuthScope authscope2;
    private BasicScheme authscheme1;
    private BasicScheme authscheme2;
    private BasicCredentialsProvider credProvider;
    private AuthState targetState;
    private AuthState proxyState;

    @Before
    public void setUp() {
        this.target = new HttpHost("localhost", 80);
        this.proxy = new HttpHost("localhost", 8080);

        this.credProvider = new BasicCredentialsProvider();
        this.creds1 = new UsernamePasswordCredentials("user1", "secret1");
        this.creds2 = new UsernamePasswordCredentials("user2", "secret2");
        this.authscope1 = new AuthScope(this.target.getHostName(), this.target.getPort());
        this.authscope2 = new AuthScope(this.proxy.getHostName(), this.proxy.getPort());
        this.authscheme1 = new BasicScheme();
        this.authscheme2 = new BasicScheme();

        this.credProvider.setCredentials(this.authscope1, this.creds1);
        this.credProvider.setCredentials(this.authscope2, this.creds2);

        this.targetState = new AuthState();
        this.proxyState = new AuthState();
    }

    @Test(expected=IllegalArgumentException.class)
    public void testRequestParameterCheck() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(null, context);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testContextParameterCheck() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");
        HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, null);
    }

    @Test
    public void testPreemptiveTargetAndProxyAuth() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.CREDS_PROVIDER, this.credProvider);
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        AuthCache authCache = new BasicAuthCache();
        authCache.put(this.target, this.authscheme1);
        authCache.put(this.proxy, this.authscheme2);

        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, context);
        Assert.assertSame(this.authscheme1, this.targetState.getAuthScheme());
        Assert.assertSame(this.creds1, this.targetState.getCredentials());
        Assert.assertSame(this.authscheme2, this.proxyState.getAuthScheme());
        Assert.assertSame(this.creds2, this.proxyState.getCredentials());
    }

    @Test
    public void testCredentialsProviderNotSet() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.CREDS_PROVIDER, null);
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        AuthCache authCache = new BasicAuthCache();
        authCache.put(this.target, this.authscheme1);
        authCache.put(this.proxy, this.authscheme2);

        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, context);
        Assert.assertNull(this.targetState.getAuthScheme());
        Assert.assertNull(this.targetState.getCredentials());
        Assert.assertNull(this.proxyState.getAuthScheme());
        Assert.assertNull(this.proxyState.getCredentials());
    }

    @Test
    public void testAuthCacheNotSet() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.CREDS_PROVIDER, this.credProvider);
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);
        context.setAttribute(ClientContext.AUTH_CACHE, null);

        HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, context);
        Assert.assertNull(this.targetState.getAuthScheme());
        Assert.assertNull(this.targetState.getCredentials());
        Assert.assertNull(this.proxyState.getAuthScheme());
        Assert.assertNull(this.proxyState.getCredentials());
    }

    @Test
    public void testAuthCacheEmpty() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.CREDS_PROVIDER, this.credProvider);
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        AuthCache authCache = new BasicAuthCache();
        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, context);
        Assert.assertNull(this.targetState.getAuthScheme());
        Assert.assertNull(this.targetState.getCredentials());
        Assert.assertNull(this.proxyState.getAuthScheme());
        Assert.assertNull(this.proxyState.getCredentials());
    }

    @Test
    public void testNoMatchingCredentials() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");

        this.credProvider.clear();

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.CREDS_PROVIDER, this.credProvider);
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        AuthCache authCache = new BasicAuthCache();
        authCache.put(this.target, this.authscheme1);
        authCache.put(this.proxy, this.authscheme2);

        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, context);
        Assert.assertNull(this.targetState.getAuthScheme());
        Assert.assertNull(this.targetState.getCredentials());
        Assert.assertNull(this.proxyState.getAuthScheme());
        Assert.assertNull(this.proxyState.getCredentials());
    }

    @Test
    public void testAuthSchemeAlreadySet() throws Exception {
        HttpRequest request = new BasicHttpRequest("GET", "/");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.CREDS_PROVIDER, this.credProvider);
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        AuthCache authCache = new BasicAuthCache();
        authCache.put(this.target, this.authscheme1);
        authCache.put(this.proxy, this.authscheme2);

        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        this.targetState.setAuthScheme(new BasicScheme());
        this.targetState.setCredentials(new UsernamePasswordCredentials("user3", "secret3"));
        this.proxyState.setAuthScheme(new BasicScheme());
        this.proxyState.setCredentials(new UsernamePasswordCredentials("user4", "secret4"));

        HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, context);
        Assert.assertNotSame(this.authscheme1, this.targetState.getAuthScheme());
        Assert.assertNotSame(this.creds1, this.targetState.getCredentials());
        Assert.assertNotSame(this.authscheme2, this.proxyState.getAuthScheme());
        Assert.assertNotSame(this.creds2, this.proxyState.getCredentials());
    }

}
