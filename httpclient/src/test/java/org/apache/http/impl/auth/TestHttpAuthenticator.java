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
package org.apache.http.impl.auth;

import java.util.LinkedList;
import java.util.Queue;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.auth.AuthOption;
import org.apache.http.auth.AuthProtocolState;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.ChallengeType;
import org.apache.http.auth.Credentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Lookup;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultAuthenticationStrategy;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

@SuppressWarnings({"boxing","static-access"})
public class TestHttpAuthenticator {

    private AuthState authState;
    private AuthScheme authScheme;
    private HttpContext context;
    private HttpHost defaultHost;
    private Credentials credentials;
    private BasicCredentialsProvider credentialsProvider;
    private Lookup<AuthSchemeProvider> authSchemeRegistry;
    private AuthCache authCache;
    private HttpAuthenticator httpAuthenticator;

    @Before
    public void setUp() throws Exception {
        this.authState = new AuthState();
        this.authScheme = Mockito.mock(AuthScheme.class);
        Mockito.when(this.authScheme.getSchemeName()).thenReturn("Basic");
        Mockito.when(this.authScheme.isComplete()).thenReturn(Boolean.TRUE);
        this.context = new BasicHttpContext();
        this.defaultHost = new HttpHost("localhost", 80);
        this.context.setAttribute(HttpCoreContext.HTTP_TARGET_HOST, this.defaultHost);
        this.credentials = Mockito.mock(Credentials.class);
        this.credentialsProvider = new BasicCredentialsProvider();
        this.credentialsProvider.setCredentials(AuthScope.ANY, this.credentials);
        this.context.setAttribute(HttpClientContext.CREDS_PROVIDER, this.credentialsProvider);
        this.authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
            .register("basic", new BasicSchemeFactory())
            .register("digest", new DigestSchemeFactory())
            .register("ntlm", new NTLMSchemeFactory()).build();
        this.context.setAttribute(HttpClientContext.AUTHSCHEME_REGISTRY, this.authSchemeRegistry);
        this.authCache = Mockito.mock(AuthCache.class);
        this.context.setAttribute(HttpClientContext.AUTH_CACHE, this.authCache);
        this.httpAuthenticator = new HttpAuthenticator();
    }

    @Test
    public void testUpdateAuthState() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=test");
        Assert.assertTrue(this.httpAuthenticator.updateAuthState(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));
        Mockito.verifyZeroInteractions(this.authCache);
    }

    @Test
    public void testAuthenticationRequestedAfterSuccess() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=test");

        this.authState.update(this.authScheme, this.credentials);
        this.authState.setState(AuthProtocolState.SUCCESS);

        Assert.assertTrue(this.httpAuthenticator.updateAuthState(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));

        Mockito.verify(this.authCache).remove(this.defaultHost);
    }

    @Test
    public void testAuthenticationNotRequestedUnchallenged() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");

        Assert.assertFalse(this.httpAuthenticator.updateAuthState(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.UNCHALLENGED, this.authState.getState());
    }

    @Test
    public void testAuthenticationNotRequestedSuccess1() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        this.authState.update(this.authScheme, this.credentials);
        this.authState.setState(AuthProtocolState.CHALLENGED);

        Assert.assertFalse(this.httpAuthenticator.updateAuthState(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.SUCCESS, this.authState.getState());

        Mockito.verify(this.authCache).put(this.defaultHost, this.authScheme);
    }

    @Test
    public void testAuthenticationNotRequestedSuccess2() throws Exception {
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "OK");
        this.authState.update(this.authScheme, this.credentials);
        this.authState.setState(AuthProtocolState.HANDSHAKE);

        Assert.assertFalse(this.httpAuthenticator.updateAuthState(
                this.defaultHost, ChallengeType.TARGET, response, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.SUCCESS, this.authState.getState());

        Mockito.verify(this.authCache).put(this.defaultHost, this.authScheme);
    }

    @Test
    public void testAuthentication() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertTrue(this.httpAuthenticator.handleAuthChallenge(
                host,ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.CHALLENGED, this.authState.getState());

        final Queue<AuthOption> options = this.authState.getAuthOptions();
        Assert.assertNotNull(options);
        final AuthOption option1 = options.poll();
        Assert.assertNotNull(option1);
        Assert.assertEquals("digest", option1.getAuthScheme().getSchemeName());
        final AuthOption option2 = options.poll();
        Assert.assertNotNull(option2);
        Assert.assertEquals("basic", option2.getAuthScheme().getSchemeName());
        Assert.assertNull(options.poll());
    }

    @Test
    public void testAuthenticationNoChallenges() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationNoSupportedChallenges() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "This realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "That realm=\"realm1\", nonce=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationNoCredentials() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));

        this.credentialsProvider.clear();

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
    }

    @Test
    public void testAuthenticationFailed() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));

        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(this.authScheme, this.credentials);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthProtocolState.FAILURE, this.authState.getState());

        Mockito.verify(this.authCache).remove(host);
    }

    @Test
    public void testAuthenticationFailedPreviously() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));

        this.authState.setState(AuthProtocolState.FAILURE);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthProtocolState.FAILURE, this.authState.getState());
    }

    @Test
    public void testAuthenticationFailure() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(new BasicScheme(), this.credentials);

        Assert.assertFalse(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.FAILURE, this.authState.getState());
        Assert.assertNull(this.authState.getCredentials());
    }

    @Test
    public void testAuthenticationHandshaking() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"test\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", stale=true, nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(new DigestScheme(), this.credentials);

        Assert.assertTrue(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthProtocolState.HANDSHAKE, this.authState.getState());
    }

    @Test
    public void testAuthenticationNoMatchingChallenge() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"realm1\", nonce=\"1234\""));
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "whatever realm=\"realm1\", stuff=\"1234\""));

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(new BasicScheme(), this.credentials);

        Assert.assertTrue(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));
        Assert.assertEquals(AuthProtocolState.CHALLENGED, this.authState.getState());

        final Queue<AuthOption> options = this.authState.getAuthOptions();
        Assert.assertNotNull(options);
        final AuthOption option1 = options.poll();
        Assert.assertNotNull(option1);
        Assert.assertEquals("digest", option1.getAuthScheme().getSchemeName());
        Assert.assertNull(options.poll());
    }

    @Test
    public void testAuthenticationException() throws Exception {
        final HttpHost host = new HttpHost("somehost", 80);
        final HttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_UNAUTHORIZED, "UNAUTHORIZED");
        response.addHeader(new BasicHeader(HttpHeaders.WWW_AUTHENTICATE, "blah blah blah"));

        this.authState.setState(AuthProtocolState.CHALLENGED);

        final DefaultAuthenticationStrategy authStrategy = new DefaultAuthenticationStrategy();

        Assert.assertFalse(this.httpAuthenticator.handleAuthChallenge(
                host, ChallengeType.TARGET, response, authStrategy, this.authState, this.context));

        Assert.assertEquals(AuthProtocolState.UNCHALLENGED, this.authState.getState());
        Assert.assertNull(this.authState.getAuthScheme());
        Assert.assertNull(this.authState.getCredentials());
    }

    @Test
    public void testAuthFailureState() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.FAILURE);
        this.authState.update(this.authScheme, this.credentials);

        this.httpAuthenticator.generateAuthResponse(request, authState, context);

        Assert.assertFalse(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme, Mockito.never()).authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
    }

    @Test
    public void testAuthChallengeStateNoOption() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.CHALLENGED);
        this.authState.update(this.authScheme, this.credentials);

        Mockito.when(this.authScheme.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(HttpHeaders.AUTHORIZATION, "stuff"));

        this.httpAuthenticator.generateAuthResponse(request, authState, context);

        Assert.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme).authenticate(this.credentials, request, this.context);
    }

    @Test
    public void testAuthChallengeStateOneOptions() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.CHALLENGED);
        final LinkedList<AuthOption> authOptions = new LinkedList<>();
        authOptions.add(new AuthOption(this.authScheme, this.credentials));
        this.authState.update(authOptions);

        Mockito.when(this.authScheme.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(HttpHeaders.AUTHORIZATION, "stuff"));

        this.httpAuthenticator.generateAuthResponse(request, authState, context);

        Assert.assertSame(this.authScheme, this.authState.getAuthScheme());
        Assert.assertSame(this.credentials, this.authState.getCredentials());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme).authenticate(this.credentials, request, this.context);
    }

    @Test
    public void testAuthChallengeStateMultipleOption() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.CHALLENGED);

        final LinkedList<AuthOption> authOptions = new LinkedList<>();
        final AuthScheme authScheme1 = Mockito.mock(AuthScheme.class);
        Mockito.doThrow(new AuthenticationException()).when(authScheme1).authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
        final AuthScheme authScheme2 = Mockito.mock(AuthScheme.class);
        Mockito.when(authScheme2.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(HttpHeaders.AUTHORIZATION, "stuff"));
        authOptions.add(new AuthOption(authScheme1, this.credentials));
        authOptions.add(new AuthOption(authScheme2, this.credentials));
        this.authState.update(authOptions);

        this.httpAuthenticator.generateAuthResponse(request, authState, context);

        Assert.assertSame(authScheme2, this.authState.getAuthScheme());
        Assert.assertSame(this.credentials, this.authState.getCredentials());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(authScheme1, Mockito.times(1)).authenticate(this.credentials, request, this.context);
        Mockito.verify(authScheme2, Mockito.times(1)).authenticate(this.credentials, request, this.context);
    }

    @Test
    public void testAuthSuccess() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.SUCCESS);
        this.authState.update(this.authScheme, this.credentials);

        Mockito.when(this.authScheme.isConnectionBased()).thenReturn(Boolean.FALSE);
        Mockito.when(this.authScheme.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(HttpHeaders.AUTHORIZATION, "stuff"));

        this.httpAuthenticator.generateAuthResponse(request, authState, context);

        Assert.assertSame(this.authScheme, this.authState.getAuthScheme());
        Assert.assertSame(this.credentials, this.authState.getCredentials());
        Assert.assertNull(this.authState.getAuthOptions());

        Assert.assertTrue(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme).authenticate(this.credentials, request, this.context);
    }

    @Test
    public void testAuthSuccessConnectionBased() throws Exception {
        final HttpRequest request = new BasicHttpRequest("GET", "/");
        this.authState.setState(AuthProtocolState.SUCCESS);
        this.authState.update(this.authScheme, this.credentials);

        Mockito.when(this.authScheme.isConnectionBased()).thenReturn(Boolean.TRUE);
        Mockito.when(this.authScheme.authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class))).thenReturn(new BasicHeader(HttpHeaders.AUTHORIZATION, "stuff"));

        this.httpAuthenticator.generateAuthResponse(request, authState, context);

        Assert.assertFalse(request.containsHeader(HttpHeaders.AUTHORIZATION));

        Mockito.verify(this.authScheme, Mockito.never()).authenticate(
                Mockito.any(Credentials.class),
                Mockito.any(HttpRequest.class),
                Mockito.any(HttpContext.class));
    }

}
