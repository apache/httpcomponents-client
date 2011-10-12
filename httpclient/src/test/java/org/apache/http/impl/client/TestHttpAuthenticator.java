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
package org.apache.http.impl.client;

import java.util.HashMap;
import java.util.Queue;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AUTH;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeRegistry;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.client.AuthCache;
import org.apache.http.client.AuthenticationStrategy;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.auth.BasicSchemeFactory;
import org.apache.http.impl.auth.DigestScheme;
import org.apache.http.impl.auth.DigestSchemeFactory;
import org.apache.http.impl.auth.NTLMSchemeFactory;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class TestHttpAuthenticator {

    private AuthenticationStrategy authStrategy;
    private AuthState authState;
    private AuthScheme authScheme;
    private HttpContext context;
    private HttpHost host;
    private HttpHost proxy;
    private Credentials credentials;
    private BasicCredentialsProvider credentialsProvider;
    private AuthSchemeRegistry authSchemeRegistry;
    private AuthCache authCache;
    private HttpAuthenticator httpAuthenticator;

    @Before
    public void setUp() throws Exception {
        this.authStrategy = Mockito.mock(AuthenticationStrategy.class);
        this.authState = new AuthState();
        this.authScheme = new BasicScheme();
        this.authScheme.processChallenge(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=test"));
        this.context = new BasicHttpContext();
        this.host = new HttpHost("localhost", 80);
        this.proxy = new HttpHost("localhost", 8888);
        this.context.setAttribute(ExecutionContext.HTTP_TARGET_HOST, this.host);
        this.context.setAttribute(ExecutionContext.HTTP_PROXY_HOST, this.proxy);
        this.credentials = Mockito.mock(Credentials.class);
        this.credentialsProvider = new BasicCredentialsProvider();
        this.credentialsProvider.setCredentials(AuthScope.ANY, this.credentials);
        this.context.setAttribute(ClientContext.CREDS_PROVIDER, this.credentialsProvider);
        this.authSchemeRegistry = new AuthSchemeRegistry();
        this.authSchemeRegistry.register("basic", new BasicSchemeFactory());
        this.authSchemeRegistry.register("digest", new DigestSchemeFactory());
        this.authSchemeRegistry.register("ntlm", new NTLMSchemeFactory());
        this.context.setAttribute(ClientContext.AUTHSCHEME_REGISTRY, this.authSchemeRegistry);
        this.authCache = Mockito.mock(AuthCache.class);
        this.context.setAttribute(ClientContext.AUTH_CACHE, this.authCache);
        this.httpAuthenticator = new HttpAuthenticator();
    }

    @Test
    public void testAuthenticationRequested() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        Mockito.when(this.authStrategy.isAuthenticationRequested(
                Mockito.any(HttpHost.class),
                Mockito.any(HttpResponse.class),
                Mockito.any(HttpContext.class))).thenReturn(Boolean.TRUE);

        Assert.assertTrue(this.httpAuthenticator.isAuthenticationRequested(
                this.host, response, this.authStrategy, this.authState, this.context));

        Mockito.verify(this.authStrategy).isAuthenticationRequested(this.host, response, this.context);
    }

    @Test
    public void testAuthenticationNotRequestedUnchallenged() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Mockito.when(this.authStrategy.isAuthenticationRequested(
                Mockito.any(HttpHost.class),
                Mockito.any(HttpResponse.class),
                Mockito.any(HttpContext.class))).thenReturn(Boolean.FALSE);

        Assert.assertFalse(this.httpAuthenticator.isAuthenticationRequested(
                this.host, response, this.authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.UNCHALLENGED, this.authState.getState());

        Mockito.verify(this.authStrategy).isAuthenticationRequested(this.host, response, this.context);
    }

    @Test
    public void testAuthenticationNotRequestedSuccess1() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Mockito.when(this.authStrategy.isAuthenticationRequested(
                Mockito.any(HttpHost.class),
                Mockito.any(HttpResponse.class),
                Mockito.any(HttpContext.class))).thenReturn(Boolean.FALSE);
        this.authState.update(this.authScheme, this.credentials);
        this.authState.setState(AuthProtocolState.CHALLENGED);

        Assert.assertFalse(this.httpAuthenticator.isAuthenticationRequested(
                this.host, response, this.authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.SUCCESS, this.authState.getState());

        Mockito.verify(this.authStrategy).isAuthenticationRequested(this.host, response, this.context);
        Mockito.verify(this.authStrategy).authSucceeded(this.host, this.authScheme, this.context);
    }

    @Test
    public void testAuthenticationNotRequestedSuccess2() throws Exception {
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        Mockito.when(this.authStrategy.isAuthenticationRequested(
                Mockito.any(HttpHost.class),
                Mockito.any(HttpResponse.class),
                Mockito.any(HttpContext.class))).thenReturn(Boolean.FALSE);
        this.authState.update(this.authScheme, this.credentials);
        this.authState.setState(AuthProtocolState.HANDSHAKE);

        Assert.assertFalse(this.httpAuthenticator.isAuthenticationRequested(
                this.host, response, this.authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.SUCCESS, this.authState.getState());

        Mockito.verify(this.authStrategy).isAuthenticationRequested(this.host, response, this.context);
        Mockito.verify(this.authStrategy).authSucceeded(this.host, this.authScheme, this.context);
    }

    @Test
    public void testAuthentication() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "whatever realm=\"realm1\", stuff=\"1234\""));

        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();

        Assert.assertTrue(this.httpAuthenticator.authenticate(host,
                response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.CHALLENGED, this.authState.getState());

        Queue<AuthOption> options = this.authState.getAuthOptions();
        Assert.assertNotNull(options);
        AuthOption option1 = options.poll();
        Assert.assertNotNull(option1);
        Assert.assertEquals("digest", option1.getAuthScheme().getSchemeName());
        AuthOption option2 = options.poll();
        Assert.assertNotNull(option2);
        Assert.assertEquals("basic", option2.getAuthScheme().getSchemeName());
        Assert.assertNull(options.poll());
    }

    @Test
    public void testAuthenticationNoChallenges() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");

        Mockito.when(this.authStrategy.getChallenges(
                Mockito.any(HttpHost.class),
                Mockito.any(HttpResponse.class),
                Mockito.any(HttpContext.class))).thenReturn(new HashMap<String, Header>());

        Assert.assertFalse(this.httpAuthenticator.authenticate(host,
                response, this.authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationNoSupportedChallenges() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "This realm=\"test\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "That realm=\"realm1\", nonce=\"1234\""));

        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.authenticate(host,
                response, authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationNoCredentials() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", nonce=\"1234\""));

        this.credentialsProvider.clear();

        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.authenticate(host,
                response, authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationFailed() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", nonce=\"1234\""));

        this.authState.setState(AuthProtocolState.FAILURE);

        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.authenticate(host,
                response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthProtocolState.FAILURE, this.authState.getState());
    }

    @Test
    public void testAuthenticationNoAuthScheme() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", nonce=\"1234\""));

        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(this.authScheme, this.credentials);

        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.authenticate(host,
                response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthProtocolState.FAILURE, this.authState.getState());

        Mockito.verify(this.authCache).remove(host);
    }

    @Test
    public void testAuthenticationFailure() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "whatever realm=\"realm1\", stuff=\"1234\""));

        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();

        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(new BasicScheme(), this.credentials);

        Assert.assertFalse(this.httpAuthenticator.authenticate(host,
                response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.FAILURE, this.authState.getState());
        Assert.assertNull(this.authState.getCredentials());
    }

    @Test
    public void testAuthenticationHandshaking() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", stale=true, nonce=\"1234\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "whatever realm=\"realm1\", stuff=\"1234\""));

        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();

        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(new DigestScheme(), this.credentials);

        Assert.assertTrue(this.httpAuthenticator.authenticate(host,
                response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthProtocolState.HANDSHAKE, this.authState.getState());
    }

    @Test
    public void testAuthenticationNoMatchingChallenge() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(AUTH.WWW_AUTH, "whatever realm=\"realm1\", stuff=\"1234\""));

        TargetAuthenticationStrategy authStrategy = new TargetAuthenticationStrategy();

        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(new BasicScheme(), this.credentials);

        Assert.assertTrue(this.httpAuthenticator.authenticate(host,
                response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.CHALLENGED, this.authState.getState());

        Queue<AuthOption> options = this.authState.getAuthOptions();
        Assert.assertNotNull(options);
        AuthOption option1 = options.poll();
        Assert.assertNotNull(option1);
        Assert.assertEquals("digest", option1.getAuthScheme().getSchemeName());
        Assert.assertNull(options.poll());
    }

    @Test
    public void testAuthenticationException() throws Exception {
        HttpHost host = new HttpHost("somehost", 80);
        HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");

        this.authState.setState(AuthProtocolState.CHALLENGED);

        Mockito.doThrow(new MalformedChallengeException()).when(this.authStrategy).getChallenges(
                Mockito.any(HttpHost.class),
                Mockito.any(HttpResponse.class),
                Mockito.any(HttpContext.class));

        Assert.assertFalse(this.httpAuthenticator.authenticate(host,
                response, this.authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthProtocolState.UNCHALLENGED, this.authState.getState());
        Assert.assertNull(this.authState.getAuthScheme());
        Assert.assertNull(this.authState.getCredentials());
    }

}
