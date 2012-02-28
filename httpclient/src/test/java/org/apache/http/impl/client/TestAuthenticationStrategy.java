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
 */

package org.apache.http.impl.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.params.AuthPNames;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.params.AuthPolicy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Simple tests for {@link AuthenticationStrategyImpl}.
 */
public class TestAuthenticationStrategy {

    @Test(expected=IllegalArgumentException.class)
    public void testIsAuthenticationRequestedInvalidInput() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost host = new HttpHost("localhost", 80);
        HttpContext context = new BasicHttpContext();
        authStrategy.isAuthenticationRequested(host, null, context);
    }

    @Test
    public void testTargetAuthRequested() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        HttpHost host = new HttpHost("localhost", 80);
        HttpContext context = new BasicHttpContext();
        Assert.assertTrue(authStrategy.isAuthenticationRequested(host, response, context));
    }

    @Test
    public void testProxyAuthRequested() throws Exception {
        ProxyAuthenticationStrategy authStrategy = new ProxyAuthenticationStrategy();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED, "UNAUTHORIZED");
        HttpHost host = new HttpHost("localhost", 80);
        HttpContext context = new BasicHttpContext();
        Assert.assertTrue(authStrategy.isAuthenticationRequested(host, response, context));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetChallengesInvalidInput() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost host = new HttpHost("localhost", 80);
        HttpContext context = new BasicHttpContext();
        authStrategy.getChallenges(host, null, context);
    }

    @Test
    public void testGetChallenges() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpContext context = new BasicHttpContext();
        HttpHost host = new HttpHost("localhost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        Header h1 = new BasicHeader(AUTH.WWW_AUTH, "  Basic  realm=\"test\"");
        Header h2 = new BasicHeader(AUTH.WWW_AUTH, "\t\tDigest   realm=\"realm1\", nonce=\"1234\"");
        Header h3 = new BasicHeader(AUTH.WWW_AUTH, "WhatEver realm=\"realm1\", stuff=\"1234\"");
        response.addHeader(h1);
        response.addHeader(h2);
        response.addHeader(h3);

        Map<String, Header> challenges = authStrategy.getChallenges(host, response, context);

        Assert.assertNotNull(challenges);
        Assert.assertEquals(3, challenges.size());
        Assert.assertSame(h1, challenges.get("basic"));
        Assert.assertSame(h2, challenges.get("digest"));
        Assert.assertSame(h3, challenges.get("whatever"));
    }

    @Test
    public void testSelectInvalidInput() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        Map<String, Header> challenges = new HashMap<String, Header>();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        HttpHost authhost = new HttpHost("locahost", 80);
        HttpContext context = new BasicHttpContext();
        try {
            authStrategy.select(null, authhost, response, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            authStrategy.select(challenges, null, response, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            authStrategy.select(challenges, authhost, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            authStrategy.select(challenges, authhost, response, null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testSelectNoSchemeRegistry() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        HttpHost authhost = new HttpHost("locahost", 80);
        HttpContext context = new BasicHttpContext();

        Map<String, Header> challenges = new HashMap<String, Header>();
        challenges.put("basic", new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\""));
        challenges.put("digest", new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", nonce=\"1234\""));

        Queue<AuthOption> options = authStrategy.select(challenges, authhost, response, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(0, options.size());
    }

    @Test
    public void testSelectNoCredentialsProvider() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        HttpHost authhost = new HttpHost("locahost", 80);
        HttpContext context = new BasicHttpContext();

        Map<String, Header> challenges = new HashMap<String, Header>();
        challenges.put("basic", new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\""));
        challenges.put("digest", new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", nonce=\"1234\""));

        AuthSchemeRegistry authSchemeRegistry = new AuthSchemeRegistry();
        authSchemeRegistry.register("basic", new BasicSchemeFactory());
        authSchemeRegistry.register("digest", new DigestSchemeFactory());
        context.setAttribute(ClientContext.AUTHSCHEME_REGISTRY, authSchemeRegistry);

        Queue<AuthOption> options = authStrategy.select(challenges, authhost, response, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(0, options.size());
    }

    @Test
    public void testNoCredentials() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        HttpHost authhost = new HttpHost("locahost", 80);
        HttpContext context = new BasicHttpContext();

        Map<String, Header> challenges = new HashMap<String, Header>();
        challenges.put("basic", new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"realm1\""));
        challenges.put("digest", new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm2\", nonce=\"1234\""));

        AuthSchemeRegistry authSchemeRegistry = new AuthSchemeRegistry();
        authSchemeRegistry.register("basic", new BasicSchemeFactory());
        authSchemeRegistry.register("digest", new DigestSchemeFactory());
        context.setAttribute(ClientContext.AUTHSCHEME_REGISTRY, authSchemeRegistry);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        context.setAttribute(ClientContext.CREDS_PROVIDER, credentialsProvider);

        Queue<AuthOption> options = authStrategy.select(challenges, authhost, response, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(0, options.size());
    }

    @Test
    public void testCredentialsFound() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        HttpHost authhost = new HttpHost("somehost", 80);
        HttpContext context = new BasicHttpContext();

        Map<String, Header> challenges = new HashMap<String, Header>();
        challenges.put("basic", new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"realm1\""));
        challenges.put("digest", new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm2\", nonce=\"1234\""));

        AuthSchemeRegistry authSchemeRegistry = new AuthSchemeRegistry();
        authSchemeRegistry.register("basic", new BasicSchemeFactory());
        authSchemeRegistry.register("digest", new DigestSchemeFactory());
        context.setAttribute(ClientContext.AUTHSCHEME_REGISTRY, authSchemeRegistry);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("somehost", 80, "realm2"),
                new UsernamePasswordCredentials("user", "pwd"));
        context.setAttribute(ClientContext.CREDS_PROVIDER, credentialsProvider);

        Queue<AuthOption> options = authStrategy.select(challenges, authhost, response, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(1, options.size());
        AuthOption option = options.remove();
        Assert.assertTrue(option.getAuthScheme() instanceof DigestScheme);
    }

    @Test
    public void testUnsupportedScheme() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        HttpHost authhost = new HttpHost("somehost", 80);
        HttpContext context = new BasicHttpContext();

        Map<String, Header> challenges = new HashMap<String, Header>();
        challenges.put("basic", new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"realm1\""));
        challenges.put("digest", new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm2\", nonce=\"1234\""));
        challenges.put("whatever", new BasicHeader(AUTH.WWW_AUTH, "Whatever realm=\"realm3\""));

        AuthSchemeRegistry authSchemeRegistry = new AuthSchemeRegistry();
        authSchemeRegistry.register("basic", new BasicSchemeFactory());
        authSchemeRegistry.register("digest", new DigestSchemeFactory());
        context.setAttribute(ClientContext.AUTHSCHEME_REGISTRY, authSchemeRegistry);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("somehost", 80),
                new UsernamePasswordCredentials("user", "pwd"));
        context.setAttribute(ClientContext.CREDS_PROVIDER, credentialsProvider);

        Queue<AuthOption> options = authStrategy.select(challenges, authhost, response, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(2, options.size());
        AuthOption option1 = options.remove();
        Assert.assertTrue(option1.getAuthScheme() instanceof DigestScheme);
        AuthOption option2 = options.remove();
        Assert.assertTrue(option2.getAuthScheme() instanceof BasicScheme);
    }

    @Test
    public void testCustomAuthPreference() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.getParams().setParameter(AuthPNames.TARGET_AUTH_PREF,
                Arrays.asList(new String[] {AuthPolicy.BASIC } ));
        HttpHost authhost = new HttpHost("somehost", 80);
        HttpContext context = new BasicHttpContext();

        Map<String, Header> challenges = new HashMap<String, Header>();
        challenges.put("basic", new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"realm1\""));
        challenges.put("digest", new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm2\", nonce=\"1234\""));

        AuthSchemeRegistry authSchemeRegistry = new AuthSchemeRegistry();
        authSchemeRegistry.register("basic", new BasicSchemeFactory());
        authSchemeRegistry.register("digest", new DigestSchemeFactory());
        context.setAttribute(ClientContext.AUTHSCHEME_REGISTRY, authSchemeRegistry);

        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope("somehost", 80),
                new UsernamePasswordCredentials("user", "pwd"));
        context.setAttribute(ClientContext.CREDS_PROVIDER, credentialsProvider);

        Queue<AuthOption> options = authStrategy.select(challenges, authhost, response, context);
        Assert.assertNotNull(options);
        Assert.assertEquals(1, options.size());
        AuthOption option1 = options.remove();
        Assert.assertTrue(option1.getAuthScheme() instanceof BasicScheme);
    }

    @Test
    public void testAuthSucceededInvalidInput() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost authhost = new HttpHost("locahost", 80);
        BasicScheme authScheme = new BasicScheme();
        HttpContext context = new BasicHttpContext();
        try {
            authStrategy.authSucceeded(null, authScheme, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            authStrategy.authSucceeded(authhost, null, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            authStrategy.authSucceeded(authhost, authScheme, null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testAuthSucceeded() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost authhost = new HttpHost("somehost", 80);
        BasicScheme authScheme = new BasicScheme();
        authScheme.processChallenge(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=test"));

        AuthCache authCache = Mockito.mock(AuthCache.class);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        authStrategy.authSucceeded(authhost, authScheme, context);
        Mockito.verify(authCache).put(authhost, authScheme);
    }

    @Test
    public void testAuthSucceededNoCache() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost authhost = new HttpHost("somehost", 80);
        BasicScheme authScheme = new BasicScheme();
        authScheme.processChallenge(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=test"));

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.AUTH_CACHE, null);

        authStrategy.authSucceeded(authhost, authScheme, context);
        AuthCache authCache = (AuthCache) context.getAttribute(ClientContext.AUTH_CACHE);
        Assert.assertNotNull(authCache);
    }

    @Test
    public void testAuthScemeNotCompleted() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost authhost = new HttpHost("somehost", 80);
        BasicScheme authScheme = new BasicScheme();

        AuthCache authCache = Mockito.mock(AuthCache.class);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        authStrategy.authSucceeded(authhost, authScheme, context);
        Mockito.verify(authCache, Mockito.never()).put(authhost, authScheme);
    }

    @Test
    public void testAuthScemeNonCacheable() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost authhost = new HttpHost("somehost", 80);
        AuthScheme authScheme = Mockito.mock(AuthScheme.class);
        Mockito.when(authScheme.isComplete()).thenReturn(true);
        Mockito.when(authScheme.getSchemeName()).thenReturn("whatever");

        AuthCache authCache = Mockito.mock(AuthCache.class);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        authStrategy.authSucceeded(authhost, authScheme, context);
        Mockito.verify(authCache, Mockito.never()).put(authhost, authScheme);
    }

    @Test
    public void testAuthFailedInvalidInput() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost authhost = new HttpHost("locahost", 80);
        BasicScheme authScheme = new BasicScheme();
        HttpContext context = new BasicHttpContext();
        try {
            authStrategy.authFailed(null, authScheme, context);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
        try {
            authStrategy.authFailed(authhost, authScheme, null);
            Assert.fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException ex) {
        }
    }

    @Test
    public void testAuthFailed() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost authhost = new HttpHost("somehost", 80);
        BasicScheme authScheme = new BasicScheme();
        authScheme.processChallenge(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=test"));

        AuthCache authCache = Mockito.mock(AuthCache.class);

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.AUTH_CACHE, authCache);

        authStrategy.authFailed(authhost, authScheme, context);
        Mockito.verify(authCache).remove(authhost);
    }

    @Test
    public void testAuthFailedNoCache() throws Exception {
        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();
        HttpHost authhost = new HttpHost("somehost", 80);
        BasicScheme authScheme = new BasicScheme();

        HttpContext context = new BasicHttpContext();
        context.setAttribute(ClientContext.AUTH_CACHE, null);

        authStrategy.authFailed(authhost, authScheme, context);
    }

}
