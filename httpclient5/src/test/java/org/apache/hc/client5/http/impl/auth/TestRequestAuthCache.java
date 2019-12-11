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
package org.apache.hc.client5.http.impl.auth;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthExchange;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RequestAuthCache;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.Assert;
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

    @Before
    public void setUp() {
        this.target = new HttpHost("localhost", 80);
        this.proxy = new HttpHost("localhost", 8080);

        this.credProvider = new BasicCredentialsProvider();
        this.creds1 = new UsernamePasswordCredentials("user1", "secret1".toCharArray());
        this.creds2 = new UsernamePasswordCredentials("user2", "secret2".toCharArray());
        this.authscope1 = new AuthScope(this.target);
        this.authscope2 = new AuthScope(this.proxy);
        this.authscheme1 = new BasicScheme();
        this.authscheme2 = new BasicScheme();

        this.credProvider.setCredentials(this.authscope1, this.creds1);
        this.credProvider.setCredentials(this.authscope2, this.creds2);
    }

    @Test(expected=NullPointerException.class)
    public void testRequestParameterCheck() throws Exception {
        final HttpClientContext context = HttpClientContext.create();
        final HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(null, null, context);
    }

    @Test(expected=NullPointerException.class)
    public void testContextParameterCheck() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        final HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, null, null);
    }

    @Test
    public void testPreemptiveTargetAndProxyAuth() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.CREDS_PROVIDER, this.credProvider);
        context.setAttribute(HttpClientContext.HTTP_ROUTE, new HttpRoute(this.target, null, this.proxy, false));

        final AuthCache authCache = new BasicAuthCache();
        authCache.put(this.target, this.authscheme1);
        authCache.put(this.proxy, this.authscheme2);

        context.setAttribute(HttpClientContext.AUTH_CACHE, authCache);

        final HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, null, context);

        final AuthExchange targetAuthExchange = context.getAuthExchange(this.target);
        final AuthExchange proxyAuthExchange = context.getAuthExchange(this.proxy);

        Assert.assertNotNull(targetAuthExchange);
        Assert.assertNotNull(targetAuthExchange.getAuthScheme());
        Assert.assertNotNull(proxyAuthExchange);
        Assert.assertNotNull(proxyAuthExchange.getAuthScheme());
    }

    @Test
    public void testCredentialsProviderNotSet() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.CREDS_PROVIDER, null);
        context.setAttribute(HttpClientContext.HTTP_ROUTE, new HttpRoute(this.target, null, this.proxy, false));

        final AuthCache authCache = new BasicAuthCache();
        authCache.put(this.target, this.authscheme1);
        authCache.put(this.proxy, this.authscheme2);

        context.setAttribute(HttpClientContext.AUTH_CACHE, authCache);

        final HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, null, context);

        final AuthExchange targetAuthExchange = context.getAuthExchange(this.target);
        final AuthExchange proxyAuthExchange = context.getAuthExchange(this.proxy);

        Assert.assertNotNull(targetAuthExchange);
        Assert.assertNull(targetAuthExchange.getAuthScheme());
        Assert.assertNotNull(proxyAuthExchange);
        Assert.assertNull(proxyAuthExchange.getAuthScheme());
    }

    @Test
    public void testAuthCacheNotSet() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.CREDS_PROVIDER, this.credProvider);
        context.setAttribute(HttpClientContext.HTTP_ROUTE, new HttpRoute(this.target, null, this.proxy, false));
        context.setAttribute(HttpClientContext.AUTH_CACHE, null);

        final HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, null, context);

        final AuthExchange targetAuthExchange = context.getAuthExchange(this.target);
        final AuthExchange proxyAuthExchange = context.getAuthExchange(this.proxy);

        Assert.assertNotNull(targetAuthExchange);
        Assert.assertNull(targetAuthExchange.getAuthScheme());
        Assert.assertNotNull(proxyAuthExchange);
        Assert.assertNull(proxyAuthExchange.getAuthScheme());
    }

    @Test
    public void testAuthCacheEmpty() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");

        final HttpClientContext context = HttpClientContext.create();
        context.setAttribute(HttpClientContext.CREDS_PROVIDER, this.credProvider);
        context.setAttribute(HttpClientContext.HTTP_ROUTE, new HttpRoute(this.target, null, this.proxy, false));

        final AuthCache authCache = new BasicAuthCache();
        context.setAttribute(HttpClientContext.AUTH_CACHE, authCache);

        final HttpRequestInterceptor interceptor = new RequestAuthCache();
        interceptor.process(request, null, context);

        final AuthExchange targetAuthExchange = context.getAuthExchange(this.target);
        final AuthExchange proxyAuthExchange = context.getAuthExchange(this.proxy);

        Assert.assertNotNull(targetAuthExchange);
        Assert.assertNull(targetAuthExchange.getAuthScheme());
        Assert.assertNotNull(proxyAuthExchange);
        Assert.assertNull(proxyAuthExchange.getAuthScheme());
    }

}
