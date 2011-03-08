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
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;

public class TestResponseAuthCache {

    private HttpHost target;
    private HttpHost proxy;
    private Credentials creds1;
    private Credentials creds2;
    private AuthScope authscope1;
    private AuthScope authscope2;
    private BasicScheme authscheme1;
    private BasicScheme authscheme2;
    private AuthState targetState;
    private AuthState proxyState;

    @Before
    public void setUp() throws Exception {
        this.target = new HttpHost("localhost", 80);
        this.proxy = new HttpHost("localhost", 8080);

        this.creds1 = new UsernamePasswordCredentials("user1", "secret1");
        this.creds2 = new UsernamePasswordCredentials("user2", "secret2");
        this.authscope1 = new AuthScope(this.target.getHostName(), this.target.getPort());
        this.authscope2 = new AuthScope(this.proxy.getHostName(), this.proxy.getPort());
        this.authscheme1 = new BasicScheme();
        this.authscheme2 = new BasicScheme();

        this.targetState = new AuthState();
        this.proxyState = new AuthState();
    }

    @Test(expected=IllegalArgumentException.class)
    public void testResponseParameterCheck() throws Exception {
        HttpContext context = new BasicHttpContext();
        HttpResponseInterceptor interceptor = new ResponseAuthCache();
        interceptor.process(null, context);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testContextParameterCheck() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");
        HttpResponseInterceptor interceptor = new ResponseAuthCache();
        interceptor.process(response, null);
    }

    @Test
    public void testTargetAndProxyAuthCaching() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        this.authscheme1.processChallenge(
                new BasicHeader(AUTH.WWW_AUTH, "BASIC realm=auth-realm"));
        this.authscheme2.processChallenge(
                new BasicHeader(AUTH.PROXY_AUTH, "BASIC realm=auth-realm"));

        this.targetState.setAuthScheme(this.authscheme1);
        this.targetState.setCredentials(this.creds1);
        this.targetState.setAuthScope(this.authscope1);

        this.proxyState.setAuthScheme(this.authscheme2);
        this.proxyState.setCredentials(this.creds2);
        this.proxyState.setAuthScope(this.authscope2);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        HttpResponseInterceptor interceptor = new ResponseAuthCache();
        interceptor.process(response, context);

        AuthCache authCache = (AuthCache) context.getAttribute(ClientContext.AUTH_CACHE);
        Assert.assertNotNull(authCache);
        Assert.assertSame(this.authscheme1, authCache.get(this.target));
        Assert.assertSame(this.authscheme2, authCache.get(this.proxy));
    }

    @Test
    public void testNoAuthStateInitialized() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);

        HttpResponseInterceptor interceptor = new ResponseAuthCache();
        interceptor.process(response, context);

        AuthCache authCache = (AuthCache) context.getAttribute(ClientContext.AUTH_CACHE);
        Assert.assertNull(authCache);
    }

    @Test
    public void testNoAuthSchemeSelected() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        HttpResponseInterceptor interceptor = new ResponseAuthCache();
        interceptor.process(response, context);

        AuthCache authCache = (AuthCache) context.getAttribute(ClientContext.AUTH_CACHE);
        Assert.assertNull(authCache);
    }

    @Test
    public void testAuthSchemeNotCompleted() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        this.targetState.setAuthScheme(this.authscheme1);
        this.targetState.setCredentials(this.creds1);
        this.targetState.setAuthScope(this.authscope1);

        this.proxyState.setAuthScheme(this.authscheme2);
        this.proxyState.setCredentials(this.creds2);
        this.proxyState.setAuthScope(this.authscope2);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        HttpResponseInterceptor interceptor = new ResponseAuthCache();
        interceptor.process(response, context);

        AuthCache authCache = (AuthCache) context.getAttribute(ClientContext.AUTH_CACHE);
        Assert.assertNull(authCache);
    }

    @Test
    public void testNotChallenged() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        this.authscheme1.processChallenge(
                new BasicHeader(AUTH.WWW_AUTH, "BASIC realm=auth-realm"));
        this.authscheme2.processChallenge(
                new BasicHeader(AUTH.PROXY_AUTH, "BASIC realm=auth-realm"));

        this.targetState.setAuthScheme(this.authscheme1);
        this.targetState.setCredentials(this.creds1);
        this.targetState.setAuthScope(null);

        this.proxyState.setAuthScheme(this.authscheme2);
        this.proxyState.setCredentials(this.creds2);
        this.proxyState.setAuthScope(null);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        HttpResponseInterceptor interceptor = new ResponseAuthCache();
        interceptor.process(response, context);

        AuthCache authCache = (AuthCache) context.getAttribute(ClientContext.AUTH_CACHE);
        Assert.assertNotNull(authCache);
        Assert.assertNull(authCache.get(this.target));
        Assert.assertNull(authCache.get(this.proxy));
    }

    @Test
    public void testInvalidateCachingOnAuthFailure() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, 200, "OK");

        this.authscheme1.processChallenge(
                new BasicHeader(AUTH.WWW_AUTH, "BASIC realm=auth-realm"));
        this.authscheme2.processChallenge(
                new BasicHeader(AUTH.PROXY_AUTH, "BASIC realm=auth-realm"));

        this.targetState.setAuthScheme(this.authscheme1);
        this.targetState.setCredentials(null);
        this.targetState.setAuthScope(this.authscope1);

        this.proxyState.setAuthScheme(this.authscheme2);
        this.proxyState.setCredentials(null);
        this.proxyState.setAuthScope(this.authscope2);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.target);
        context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        context.setAttribute(ClientContext.TARGET_AUTH_STATE, this.targetState);
        context.setAttribute(ClientContext.PROXY_AUTH_STATE, this.proxyState);

        AuthCache authCache = new BasicAuthCache();
        authCache.put(this.target, this.authscheme1);
        authCache.put(this.proxy, this.authscheme2);

        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        HttpResponseInterceptor interceptor = new ResponseAuthCache();
        interceptor.process(response, context);

        Assert.assertNull(authCache.get(this.target));
        Assert.assertNull(authCache.get(this.proxy));
    }

}
